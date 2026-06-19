import json
import logging
import os
import threading
import time
import uuid
from datetime import datetime
from typing import Any, Callable, Dict, List, Optional

from edge_sdk.cache import EventCache
from edge_sdk.config import EdgeNodeConfig
from edge_sdk.heartbeat import HeartbeatManager
from edge_sdk.network_monitor import NetworkMonitor
from edge_sdk.stream_capture import StreamCapture
from edge_sdk.tensorrt_detector import TensorRTDetector
from edge_sdk.uploader import EventUploader

logger = logging.getLogger(__name__)


class EdgeNodeClient:
    def __init__(
        self,
        config: Optional[EdgeNodeConfig] = None,
        cache: Optional[EventCache] = None,
        heartbeat_mgr: Optional[HeartbeatManager] = None,
        uploader: Optional[EventUploader] = None,
        network_monitor: Optional[NetworkMonitor] = None,
        detector: Optional[TensorRTDetector] = None,
    ):
        self.config = config or EdgeNodeConfig()
        self.cache = cache or EventCache(self.config.cache_db_path)
        self.network_monitor = network_monitor or NetworkMonitor(
            server_url=self.config.server_url,
        )
        self.uploader = uploader or EventUploader(
            server_url=self.config.server_url,
            node_code=self.config.node_code,
            cache=self.cache,
            retry_max=self.config.retry_max,
            retry_initial_delay=self.config.retry_initial_delay,
            is_online_callback=self.network_monitor.is_online,
        )
        self.heartbeat_mgr = heartbeat_mgr or HeartbeatManager(
            interval=self.config.heartbeat_interval,
            node_code=self.config.node_code,
            node_name=self.config.node_name,
            send_callback=self._send_heartbeat_callback,
            get_queue_size_callback=self.cache.get_queue_size,
            is_online_callback=self.network_monitor.is_online,
        )
        self.detector = detector
        self._streams: Dict[int, StreamCapture] = {}
        self._clip_buffer: Dict[int, List] = {}
        self._clip_lock = threading.Lock()
        self._lock = threading.Lock()
        self._started = False
        self._snapshot_dir = getattr(self.config, "snapshot_dir", "./snapshots")
        self._clip_dir = getattr(self.config, "clip_dir", "./clips")

    def _send_heartbeat_callback(self, heartbeat_data: Dict[str, Any]) -> bool:
        return self.uploader.send_heartbeat(heartbeat_data)

    def _on_network_status_change(self, is_online: bool):
        status = "online" if is_online else "offline"
        logger.info(f"Network status changed: {status}")
        if is_online:
            logger.info("Network back online, triggering pending event upload")
            self.uploader.trigger_upload()

    def register(self) -> bool:
        logger.info(
            f"Registering edge node: code={self.config.node_code}, name={self.config.node_name}"
        )
        return self.uploader.register_node(self.config.node_name)

    def set_detector(self, detector: TensorRTDetector):
        self.detector = detector
        logger.info("Detector set on EdgeNodeClient")

    def add_stream(
        self,
        stream_url: str,
        camera_id: int,
        camera_name: str = "",
        fps: float = 25.0,
    ):
        if camera_id in self._streams:
            logger.warning(f"Stream already exists for camera {camera_id}")
            return
        capture = StreamCapture(
            stream_url=stream_url,
            camera_id=camera_id,
            camera_name=camera_name,
            fps=fps,
            on_frame_callback=lambda frame_no, frame, ts: self._on_frame(
                camera_id, camera_name, frame_no, frame, ts
            ),
        )
        self._streams[camera_id] = capture
        with self._clip_lock:
            self._clip_buffer[camera_id] = []
        logger.info(f"Stream added: camera={camera_name}({camera_id}), url={stream_url}")
        if self._started:
            capture.start()

    def remove_stream(self, camera_id: int):
        capture = self._streams.pop(camera_id, None)
        if capture:
            capture.stop()
        with self._clip_lock:
            self._clip_buffer.pop(camera_id, None)
        logger.info(f"Stream removed: camera_id={camera_id}")

    def _on_frame(self, camera_id: int, camera_name: str, frame_no: int, frame, frame_ts: float):
        with self._clip_lock:
            buf = self._clip_buffer.get(camera_id)
            if buf is not None:
                buf.append((frame.copy(), frame_ts))
                max_clip_frames = int(getattr(self.config, "clip_seconds", 10) * 25)
                while len(buf) > max_clip_frames:
                    buf.pop(0)

        if self.detector is None:
            return

        if frame_no % max(1, int(25 / getattr(self.config, "detect_fps", 2))) != 0:
            return

        try:
            detections = self.detector.detect(frame)
        except Exception as e:
            logger.error(f"Detection error: {e}")
            return

        for det in detections:
            label = det.get("label", "unknown")
            event_type = self.detector.EVENT_TYPE_MAP.get(label, label.upper())
            if not self.detector.check_cooldown(event_type):
                continue

            event_data = self.detector.build_event_data(
                det, camera_id, camera_name, frame_ts
            )
            self.detector.mark_event(event_type)

            snapshot_path = None
            try:
                os.makedirs(self._snapshot_dir, exist_ok=True)
                snapshot_path = os.path.join(
                    self._snapshot_dir,
                    f"{event_type}_{camera_id}_{int(frame_ts)}_{uuid.uuid4().hex[:6]}.jpg"
                )
                saved = self._streams[camera_id].capture_snapshot(frame, snapshot_path)
                if not saved:
                    snapshot_path = None
            except Exception as e:
                logger.warning(f"Snapshot capture failed: {e}")
                snapshot_path = None

            clip_path = None
            try:
                clip_path = self._save_clip(camera_id, event_type, camera_name, frame_ts)
            except Exception as e:
                logger.warning(f"Clip save failed: {e}")

            result = self.submit_event(
                event_type=event_type,
                event_data=event_data,
                snapshot_path=snapshot_path,
                video_path=clip_path,
            )
            logger.info(
                f"Edge AI event: type={event_type}, camera={camera_name}, "
                f"conf={det.get('confidence', 0):.2f}, "
                f"uploaded={result.get('uploaded')}, cached={result.get('cached')}"
            )

    def _save_clip(
        self, camera_id: int, event_type: str, camera_name: str, event_ts: float
    ) -> Optional[str]:
        try:
            import cv2
            import numpy as np
        except ImportError:
            return None

        with self._clip_lock:
            buf = self._clip_buffer.get(camera_id, [])
            if not buf:
                return None
            frames = list(buf)

        os.makedirs(self._clip_dir, exist_ok=True)
        clip_filename = f"{event_type}_{camera_id}_{int(event_ts)}_{uuid.uuid4().hex[:6]}.mp4"
        clip_path = os.path.join(self._clip_dir, clip_filename)

        try:
            h, w = frames[0][0].shape[:2]
            fourcc = cv2.VideoWriter_fourcc(*"mp4v")
            writer = cv2.VideoWriter(clip_path, fourcc, 25.0, (w, h))
            for frame, _ in frames:
                writer.write(frame)
            writer.release()
            logger.info(f"Event clip saved: {clip_path} ({len(frames)} frames)")
            return clip_path
        except Exception as e:
            logger.error(f"Failed to save clip: {e}")
            return None

    def submit_event(
        self,
        event_type: str,
        event_data: Dict[str, Any],
        snapshot_path: Optional[str] = None,
        video_path: Optional[str] = None,
    ) -> Dict[str, Any]:
        event_uuid = uuid.uuid4().hex
        event_time = datetime.now().isoformat()
        event_data_str = json.dumps(event_data, ensure_ascii=False)

        result = {
            "event_uuid": event_uuid,
            "event_type": event_type,
            "event_time": event_time,
            "cached": False,
            "uploaded": False,
            "success": False,
        }

        is_online = self.network_monitor.is_online()

        if is_online:
            event_payload = {
                "event_uuid": event_uuid,
                "event_type": event_type,
                "event_time": event_time,
                "event_data": event_data_str,
            }
            try:
                upload_success = self.uploader.upload_event(
                    event_payload,
                    snapshot_file=snapshot_path,
                    video_file=video_path,
                )
                if upload_success:
                    result["uploaded"] = True
                    result["success"] = True
                    self.cache.add_event(
                        event_uuid=event_uuid,
                        event_type=event_type,
                        event_time=event_time,
                        event_data=event_data_str,
                        snapshot_path=snapshot_path,
                        video_path=video_path,
                    )
                    self.cache.mark_success(event_uuid)
                    logger.info(
                        f"Event submitted and uploaded: uuid={event_uuid}, type={event_type}"
                    )
                    return result
            except Exception as e:
                logger.error(
                    f"Direct upload failed for event {event_uuid}: {e}",
                    exc_info=True,
                )

        cached = self.cache.add_event(
            event_uuid=event_uuid,
            event_type=event_type,
            event_time=event_time,
            event_data=event_data_str,
            snapshot_path=snapshot_path,
            video_path=video_path,
        )
        result["cached"] = cached
        result["success"] = cached

        if is_online and cached:
            self.uploader.trigger_upload()

        if not is_online:
            logger.info(
                f"Event cached (offline): uuid={event_uuid}, type={event_type}, "
                f"queue_size={self.cache.get_queue_size()}"
            )
        else:
            logger.info(
                f"Event cached for retry: uuid={event_uuid}, type={event_type}"
            )

        return result

    def start(self):
        with self._lock:
            if self._started:
                logger.warning("EdgeNodeClient already started")
                return

            logger.info(
                f"Starting EdgeNodeClient: node={self.config.node_code}, "
                f"server={self.config.server_url}"
            )

            if self.detector:
                self.detector.load_engine()
                logger.info("Detector engine loaded")

            self.network_monitor.start_monitoring(self._on_network_status_change)
            self.uploader.start()
            self.heartbeat_mgr.start()

            for camera_id, capture in self._streams.items():
                capture.start()
                logger.info(f"Stream capture started: camera_id={camera_id}")

            self._started = True
            logger.info("EdgeNodeClient started successfully")

    def stop(self):
        with self._lock:
            if not self._started:
                logger.warning("EdgeNodeClient not running")
                return

            logger.info("Stopping EdgeNodeClient...")

            for camera_id, capture in self._streams.items():
                try:
                    capture.stop()
                except Exception as e:
                    logger.error(f"Error stopping stream {camera_id}: {e}")

            if self.detector:
                try:
                    self.detector.release()
                except Exception as e:
                    logger.error(f"Error releasing detector: {e}")

            try:
                self.heartbeat_mgr.stop()
            except Exception as e:
                logger.error(f"Error stopping heartbeat manager: {e}")

            try:
                self.uploader.stop()
            except Exception as e:
                logger.error(f"Error stopping event uploader: {e}")

            try:
                self.network_monitor.stop()
            except Exception as e:
                logger.error(f"Error stopping network monitor: {e}")

            self._started = False
            logger.info("EdgeNodeClient stopped")

    def get_status(self) -> Dict[str, Any]:
        stream_status = {}
        for cid, cap in self._streams.items():
            stream_status[cid] = {
                "connected": cap.is_connected(),
                "frame_count": cap._frame_count,
            }
        return {
            "node_code": self.config.node_code,
            "node_name": self.config.node_name,
            "server_url": self.config.server_url,
            "is_online": self.network_monitor.is_online(),
            "event_queue_size": self.cache.get_queue_size(),
            "streams": stream_status,
            "detector_loaded": self.detector.is_loaded() if self.detector else False,
            "started": self._started,
        }

import asyncio
import base64
import logging
import threading
import time
from datetime import datetime
from typing import Dict, Optional

import httpx
import numpy as np

from app.config.settings import settings
from app.core.detector import detector
from app.core.event_analyzer import event_analyzer
from app.core.tracker import get_tracker, reset_tracker
from app.schemas.event import AiEventCallbackRequest

logger = logging.getLogger(__name__)


class StreamProcessor:
    def __init__(self):
        self.active_streams: Dict[int, Dict] = {}
        self._lock = threading.Lock()

    def start_stream(
        self,
        camera_id: int,
        stream_url: Optional[str] = None,
        fps: int = 2,
        enable_track: bool = True,
        enable_event: bool = True
    ) -> Dict:
        with self._lock:
            if camera_id in self.active_streams:
                return {"success": False, "message": f"Camera {camera_id} is already processing"}

            stream_info = {
                "camera_id": camera_id,
                "stream_url": stream_url,
                "fps": fps,
                "enable_track": enable_track,
                "enable_event": enable_event,
                "running": True,
                "frame_count": 0,
                "start_time": time.time(),
                "thread": None
            }

            reset_tracker(str(camera_id))

            thread = threading.Thread(
                target=self._process_stream_loop,
                args=(stream_info,),
                daemon=True
            )
            stream_info["thread"] = thread
            thread.start()

            self.active_streams[camera_id] = stream_info

            logger.info(f"Started stream processing: camera={camera_id}, fps={fps}")
            return {
                "success": True,
                "message": f"Stream processing started for camera {camera_id}",
                "cameraId": camera_id
            }

    def stop_stream(self, camera_id: int) -> Dict:
        with self._lock:
            if camera_id not in self.active_streams:
                return {"success": False, "message": f"Camera {camera_id} is not processing"}

            self.active_streams[camera_id]["running"] = False
            stream_info = self.active_streams.pop(camera_id)

            if stream_info["thread"]:
                stream_info["thread"].join(timeout=5)

            reset_tracker(str(camera_id))

            logger.info(f"Stopped stream processing: camera={camera_id}")
            return {
                "success": True,
                "message": f"Stream processing stopped for camera {camera_id}"
            }

    def _process_stream_loop(self, stream_info: Dict):
        camera_id = stream_info["camera_id"]
        fps = stream_info["fps"]
        frame_interval = 1.0 / fps
        last_frame_time = 0

        try:
            import cv2
            cap = None
            if stream_info["stream_url"]:
                cap = cv2.VideoCapture(stream_info["stream_url"])
                if not cap.isOpened():
                    logger.warning(f"Failed to open stream: {stream_info['stream_url']}, using simulation mode")
                    cap = None

            while stream_info["running"]:
                current_time = time.time()
                if current_time - last_frame_time < frame_interval:
                    time.sleep(0.01)
                    continue

                last_frame_time = current_time
                stream_info["frame_count"] += 1

                frame = None
                if cap is not None:
                    ret, frame = cap.read()
                    if not ret:
                        frame = None

                if frame is None:
                    frame = np.random.randint(0, 256, (720, 1280, 3), dtype=np.uint8)

                self._process_frame(camera_id, frame, stream_info)

                if stream_info["frame_count"] % (fps * 30) == 0:
                    elapsed = time.time() - stream_info["start_time"]
                    logger.info(f"Stream camera={camera_id}: processed {stream_info['frame_count']} frames in {elapsed:.1f}s")

            if cap is not None:
                cap.release()

        except Exception as e:
            logger.error(f"Stream processing error for camera {camera_id}: {e}", exc_info=True)

    def _process_frame(self, camera_id: int, frame: np.ndarray, stream_info: Dict):
        try:
            detections = detector.detect(frame)

            tracked_objects = []
            if stream_info["enable_track"]:
                tracker = get_tracker(str(camera_id))
                tracked_objects = tracker.update(detections)
            else:
                tracked_objects = [
                    __import__("app.schemas.tracking", fromlist=["TrackedObject"]).TrackedObject(
                        track_id=i,
                        class_id=d.class_id,
                        class_name=d.class_name,
                        bbox=d.bbox,
                        confidence=d.confidence,
                        age=1,
                        hits=1,
                        velocity=None
                    )
                    for i, d in enumerate(detections)
                ]

            if stream_info["enable_event"]:
                events = event_analyzer.analyze(
                    camera_id=str(camera_id),
                    tracked_objects=tracked_objects,
                    frame_width=frame.shape[1],
                    frame_height=frame.shape[0]
                )

                for event in events:
                    asyncio.run(self._send_event_callback(camera_id, event, frame))

        except Exception as e:
            logger.error(f"Frame processing error for camera {camera_id}: {e}")

    async def _send_event_callback(self, camera_id: int, event, frame: Optional[np.ndarray] = None):
        if not settings.BACKEND_ENABLE_CALLBACK:
            return

        try:
            snapshot_base64 = None
            if frame is not None:
                import cv2
                _, buffer = cv2.imencode(".jpg", frame)
                snapshot_base64 = base64.b64encode(buffer).decode("utf-8")

            callback_data = AiEventCallbackRequest(
                cameraId=camera_id,
                eventType=event.event_type.value,
                eventLevel=event.severity.value,
                confidence=event.confidence,
                eventTime=event.timestamp,
                description=event.description,
                snapshotBase64=snapshot_base64,
                trackData=[obj.model_dump() for obj in event.involved_objects] if event.involved_objects else None,
                bbox=event.involved_objects[0].bbox.model_dump() if event.involved_objects else None
            )

            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.post(
                    settings.BACKEND_CALLBACK_URL,
                    json=callback_data.model_dump(mode="json")
                )
                logger.info(f"Event callback sent: camera={camera_id}, type={event.event_type.value}, status={response.status_code}")
        except Exception as e:
            logger.error(f"Failed to send event callback: {e}")

    def get_stream_status(self, camera_id: Optional[int] = None) -> Dict:
        with self._lock:
            if camera_id is not None:
                if camera_id in self.active_streams:
                    info = self.active_streams[camera_id]
                    return {
                        "cameraId": camera_id,
                        "running": info["running"],
                        "frameCount": info["frame_count"],
                        "startTime": datetime.fromtimestamp(info["start_time"]).isoformat(),
                        "fps": info["fps"]
                    }
                return {"cameraId": camera_id, "running": False}

            return {
                "activeCount": len(self.active_streams),
                "streams": [
                    {
                        "cameraId": cid,
                        "running": info["running"],
                        "frameCount": info["frame_count"],
                        "startTime": datetime.fromtimestamp(info["start_time"]).isoformat()
                    }
                    for cid, info in self.active_streams.items()
                ]
            }


stream_processor = StreamProcessor()

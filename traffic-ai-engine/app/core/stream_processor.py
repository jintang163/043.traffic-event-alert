import asyncio
import base64
import logging
import os
import threading
import time
import uuid
from collections import deque
from datetime import datetime
from typing import Deque, Dict, List, Optional, Tuple

import httpx
import numpy as np

from app.config.settings import settings
from app.core.detector import detector
from app.core.event_analyzer import event_analyzer
from app.core.tracker import get_tracker, reset_tracker, TrackState
from app.core.cross_camera_tracker import cross_camera_tracker
from app.core.feature_extractor import plate_recognizer, reid_extractor
from app.schemas.event import AiEventCallbackRequest
from app.schemas.reid import CrossCameraTrackRequest, LicensePlateResult, ReIDFeature

logger = logging.getLogger(__name__)

os.makedirs(settings.VIDEO_TEMP_DIR, exist_ok=True)


class FrameBuffer:
    def __init__(self, max_seconds: int, fps: int):
        self.max_frames = max_seconds * fps
        self.buffer: Deque[Tuple[float, np.ndarray]] = deque(maxlen=self.max_frames)
        self._lock = threading.Lock()

    def add(self, timestamp: float, frame: np.ndarray):
        with self._lock:
            self.buffer.append((timestamp, frame.copy()))

    def get_frames(self) -> List[Tuple[float, np.ndarray]]:
        with self._lock:
            return list(self.buffer)

    def clear(self):
        with self._lock:
            self.buffer.clear()


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

            frame_buffer = FrameBuffer(
                max_seconds=settings.VIDEO_PRE_RECORD_SECONDS,
                fps=fps
            )

            stream_info = {
                "camera_id": camera_id,
                "stream_url": stream_url,
                "fps": fps,
                "enable_track": enable_track,
                "enable_event": enable_event,
                "running": True,
                "frame_count": 0,
                "start_time": time.time(),
                "thread": None,
                "frame_buffer": frame_buffer,
                "last_detection_push": 0.0,
                "recording_lock": threading.Lock(),
                "is_recording": False,
                "track_point_buffer": [],
                "track_point_lock": threading.Lock(),
                "last_track_push": 0.0,
                "lost_tracks": set(),
                "registered_tracks": set(),
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

            logger.info(f"Started stream processing: camera={camera_id}, fps={fps}, buffer={settings.VIDEO_PRE_RECORD_SECONDS}s pre-record")
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

            if "frame_buffer" in stream_info:
                stream_info["frame_buffer"].clear()

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
            frame_width, frame_height = 1280, 720
            if stream_info["stream_url"]:
                try:
                    cap = cv2.VideoCapture(stream_info["stream_url"])
                    if cap.isOpened():
                        frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 1280)
                        frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 720)
                        logger.info(f"Camera {camera_id}: opened stream, {frame_width}x{frame_height}")
                    else:
                        logger.warning(f"Failed to open stream: {stream_info['stream_url']}, using simulation mode")
                        cap = None
                except Exception as cap_e:
                    logger.warning(f"Open stream exception: {cap_e}, using simulation mode")
                    cap = None

            stream_info["frame_width"] = frame_width
            stream_info["frame_height"] = frame_height

            while stream_info["running"]:
                current_time = time.time()
                if current_time - last_frame_time < frame_interval:
                    time.sleep(0.01)
                    continue

                last_frame_time = current_time
                stream_info["frame_count"] += 1

                frame = None
                if cap is not None:
                    try:
                        ret, frame = cap.read()
                        if not ret:
                            frame = None
                    except Exception:
                        frame = None

                if frame is None:
                    frame = np.random.randint(0, 256, (frame_height, frame_width, 3), dtype=np.uint8)

                stream_info["frame_buffer"].add(current_time, frame)

                tracked_objects = self._process_frame(camera_id, frame, stream_info)

                if settings.DETECTION_PUSH_ENABLED:
                    now = time.time()
                    if now - stream_info["last_detection_push"] >= settings.DETECTION_PUSH_INTERVAL:
                        stream_info["last_detection_push"] = now
                        asyncio.run(self._push_detections(camera_id, tracked_objects, frame))

                if stream_info["frame_count"] % (fps * 30) == 0:
                    elapsed = time.time() - stream_info["start_time"]
                    logger.info(
                        f"Stream camera={camera_id}: processed {stream_info['frame_count']} frames in {elapsed:.1f}s, "
                        f"buffer_size={len(stream_info['frame_buffer'].buffer)}"
                    )

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
                    if not stream_info["recording_lock"].locked():
                        threading.Thread(
                            target=self._handle_event_with_video,
                            args=(camera_id, event, frame, stream_info),
                            daemon=True
                        ).start()

            self._process_tracking_features(camera_id, frame, tracked_objects, stream_info)

            self._check_lost_tracks(camera_id, frame, stream_info)

            self._collect_track_points(camera_id, frame, tracked_objects, stream_info)

            now = time.time()
            if now - stream_info["last_track_push"] >= settings.TRACK_POINT_PUSH_INTERVAL:
                stream_info["last_track_push"] = now
                self._flush_track_points(camera_id, stream_info)

            return tracked_objects

        except Exception as e:
            logger.error(f"Frame processing error for camera {camera_id}: {e}")
            return []

    def _handle_event_with_video(self, camera_id: int, event, current_frame: np.ndarray, stream_info: Dict):
        try:
            with stream_info["recording_lock"]:
                logger.info(f"[{camera_id}] Starting event video capture: {event.event_type.value}")
                stream_info["is_recording"] = True

                event_video_url = self._capture_event_video(
                    camera_id, stream_info, current_frame,
                    event.event_type.value
                )

            asyncio.run(self._send_event_callback(camera_id, event, current_frame, event_video_url))

        except Exception as e:
            logger.error(f"Error handling event with video for camera {camera_id}: {e}", exc_info=True)
            asyncio.run(self._send_event_callback(camera_id, event, current_frame, None))
        finally:
            stream_info["is_recording"] = False

    def _capture_event_video(
        self,
        camera_id: int,
        stream_info: Dict,
        current_frame: np.ndarray,
        event_type: str
    ) -> Optional[str]:
        try:
            import cv2
        except ImportError:
            logger.warning("OpenCV not available, skipping video capture")
            return None

        fps = stream_info.get("fps", 2)
        post_seconds = settings.VIDEO_POST_RECORD_SECONDS

        pre_frames = stream_info["frame_buffer"].get_frames()
        logger.info(f"[{camera_id}] Pre-record frames: {len(pre_frames)}, will record {post_seconds}s after event")

        video_id = uuid.uuid4().hex[:16]
        timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
        filename = f"evt_{camera_id}_{timestamp}_{video_id}.mp4"
        filepath = os.path.join(settings.VIDEO_TEMP_DIR, filename)

        frame_height, frame_width = current_frame.shape[:2]
        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        out = None
        try:
            out = cv2.VideoWriter(filepath, fourcc, fps, (frame_width, frame_height))
            if not out.isOpened():
                logger.warning(f"[{camera_id}] VideoWriter failed to open, trying alternative codec")
                fourcc = cv2.VideoWriter_fourcc(*'XVID')
                filename = f"evt_{camera_id}_{timestamp}_{video_id}.avi"
                filepath = os.path.join(settings.VIDEO_TEMP_DIR, filename)
                out = cv2.VideoWriter(filepath, fourcc, fps, (frame_width, frame_height))

            for _, frame in pre_frames:
                if frame.shape[:2] != (frame_height, frame_width):
                    frame = cv2.resize(frame, (frame_width, frame_height))
                out.write(frame)

            cap = None
            if stream_info.get("stream_url"):
                try:
                    cap = cv2.VideoCapture(stream_info["stream_url"])
                except Exception:
                    cap = None

            post_frames_needed = post_seconds * fps
            post_frames_captured = 0
            frame_interval = 1.0 / fps
            last_capture = time.time()

            while post_frames_captured < post_frames_needed and stream_info.get("running", True):
                now = time.time()
                if now - last_capture < frame_interval:
                    time.sleep(0.01)
                    continue
                last_capture = now

                frame = None
                if cap is not None:
                    try:
                        ret, frame = cap.read()
                        if not ret:
                            frame = None
                    except Exception:
                        frame = None

                if frame is None:
                    frame = np.random.randint(0, 256, (frame_height, frame_width, 3), dtype=np.uint8)

                if frame.shape[:2] != (frame_height, frame_width):
                    frame = cv2.resize(frame, (frame_width, frame_height))

                stream_info["frame_buffer"].add(time.time(), frame)
                out.write(frame)
                post_frames_captured += 1

            if cap is not None:
                cap.release()

        except Exception as e:
            logger.error(f"[{camera_id}] Video recording failed: {e}", exc_info=True)
            return None
        finally:
            if out is not None:
                out.release()

        try:
            if os.path.exists(filepath) and os.path.getsize(filepath) > 1024:
                logger.info(f"[{camera_id}] Video saved: {filepath} ({os.path.getsize(filepath)} bytes)")
                return self._upload_video_to_backend(camera_id, filepath, filename)
            else:
                logger.warning(f"[{camera_id}] Video file invalid or too small: {filepath}")
                return None
        finally:
            if os.path.exists(filepath):
                try:
                    os.remove(filepath)
                except Exception:
                    pass

    def _upload_video_to_backend(self, camera_id: int, filepath: str, filename: str) -> Optional[str]:
        try:
            event_no = f"EVT{datetime.now().strftime('%Y%m%d%H%M%S')}{camera_id:04d}"

            async def _do_upload():
                async with httpx.AsyncClient(timeout=120.0) as client:
                    with open(filepath, "rb") as f:
                        files = {"video": (filename, f, "video/mp4")}
                        data = {"cameraId": str(camera_id), "eventNo": event_no}
                        resp = await client.post(
                            settings.BACKEND_VIDEO_UPLOAD_URL,
                            files=files,
                            data=data
                        )
                        if resp.status_code == 200:
                            result = resp.json()
                            if result.get("code") == 200:
                                video_url = result.get("data")
                                logger.info(f"[{camera_id}] Video uploaded successfully: {video_url}")
                                return video_url
                        logger.error(f"[{camera_id}] Video upload failed: {resp.status_code} {resp.text}")
                        return None

            loop = asyncio.new_event_loop()
            try:
                return loop.run_until_complete(_do_upload())
            finally:
                loop.close()
        except Exception as e:
            logger.error(f"[{camera_id}] Upload video exception: {e}", exc_info=True)
            return None

    def _process_tracking_features(self, camera_id: int, frame: np.ndarray,
                                    tracked_objects: List, stream_info: Dict):
        if not tracked_objects:
            return

        for obj in tracked_objects:
            track_key = obj.track_id
            if track_key in stream_info["registered_tracks"]:
                continue

            if obj.hits < 5 or obj.confidence < 0.4:
                continue

            plate_result = plate_recognizer.recognize(obj, frame)
            reid_result = reid_extractor.extract(obj, frame)

            global_id = cross_camera_tracker.register_track(
                camera_id=str(camera_id),
                local_track_id=obj.track_id,
                class_name=obj.class_name,
                reid_feature=reid_result.feature_vector if reid_result else None,
                confidence=float(obj.confidence),
                license_plate=plate_result
            )

            stream_info["registered_tracks"].add(track_key)
            logger.debug(f"Camera {camera_id}: registered track {obj.track_id} -> global {global_id}")

    def _is_leaving_frame(self, bbox, frame_width: int, frame_height: int, margin_ratio: float = 0.05) -> Tuple[bool, Optional[str]]:
        margin_w = frame_width * margin_ratio
        margin_h = frame_height * margin_ratio
        x1, y1, x2, y2 = bbox.x1, bbox.y1, bbox.x2, bbox.y2

        if x1 <= margin_w:
            return True, "left"
        if x2 >= frame_width - margin_w:
            return True, "right"
        if y1 <= margin_h:
            return True, "top"
        if y2 >= frame_height - margin_h:
            return True, "bottom"
        return False, None

    def _check_lost_tracks(self, camera_id: int, frame: np.ndarray, stream_info: Dict):
        tracker = get_tracker(str(camera_id))
        if not tracker:
            return

        frame_h, frame_w = frame.shape[:2]

        for track_id, track in list(tracker.tracks.items()):
            if track_id in stream_info["lost_tracks"]:
                continue

            is_lost = False
            reason = ""

            if track.state == TrackState.LOST or track.state == TrackState.REMOVED:
                is_lost = True
                reason = f"track_state_{track.state}"

            leaving, direction = self._is_leaving_frame(track.bbox, frame_w, frame_h)
            if leaving and track.time_since_update >= 2:
                is_lost = True
                reason = f"leaving_{direction}"

            if is_lost and track.hits >= 8:
                stream_info["lost_tracks"].add(track_id)

                plate_result = LicensePlateResult(
                    plate_number=None,
                    confidence=0.0
                )
                try:
                    from app.core.feature_extractor import plate_recognizer as pr
                    cached = pr._plate_cache.get(str(track_id))
                    if cached:
                        plate_result = cached
                except Exception:
                    pass

                reid_feat = reid_extractor.get_best_feature(track_id)

                leave_direction = 0.0
                if track.velocity:
                    import math
                    leave_direction = math.atan2(track.velocity[1], track.velocity[0]) if track.velocity else 0.0

                req = CrossCameraTrackRequest(
                    camera_id=str(camera_id),
                    leaving_track_id=track_id,
                    target_class=track.class_name,
                    license_plate=plate_result,
                    reid_feature=ReIDFeature(
                        track_id=track_id,
                        camera_id=str(camera_id),
                        feature_vector=reid_feat if reid_feat else [],
                        timestamp=time.time()
                    ) if reid_feat else None,
                    leave_direction=leave_direction,
                    leave_time=time.time()
                )

                try:
                    result = cross_camera_tracker.handle_cross_camera(req)
                    logger.info(
                        f"Camera {camera_id}: track {track_id} left ({reason}), "
                        f"handover={'success' if result.handover else 'failed'}, "
                        f"global_id={result.matched_track_id}, confidence={result.confidence:.3f}"
                    )
                except Exception as e:
                    logger.warning(f"Cross-camera handover failed for track {track_id}: {e}")

    def _collect_track_points(self, camera_id: int, frame: np.ndarray,
                              tracked_objects: List, stream_info: Dict):
        if not tracked_objects:
            return

        frame_h, frame_w = frame.shape[:2]
        now = time.time()

        points = []
        for obj in tracked_objects[:50]:
            cx = (obj.bbox.x1 + obj.bbox.x2) / 2
            cy = (obj.bbox.y1 + obj.bbox.y2) / 2

            global_id = None
            try:
                rec = cross_camera_tracker.get_track_record(str(camera_id), obj.track_id)
                if rec:
                    global_id = rec.global_track_id
            except Exception:
                pass

            point = {
                "cameraId": camera_id,
                "cameraName": "",
                "trackId": None,
                "globalTrackId": global_id,
                "localTrackId": obj.track_id,
                "frameTime": datetime.now().isoformat(),
                "pixelX": round(cx / frame_w, 4),
                "pixelY": round(cy / frame_h, 4),
                "bbox": [obj.bbox.x1, obj.bbox.y1, obj.bbox.x2, obj.bbox.y2],
                "speed": 0.0,
                "direction": 0.0,
                "confidence": float(obj.confidence),
                "isKeyPoint": 0,
                "className": obj.class_name,
            }

            if obj.velocity:
                import math
                vx, vy = obj.velocity
                speed = math.sqrt(vx * vx + vy * vy)
                direction = math.atan2(vy, vx)
                point["speed"] = round(speed, 2)
                point["direction"] = round(direction, 4)

            points.append(point)

        with stream_info["track_point_lock"]:
            stream_info["track_point_buffer"].extend(points)

    def _flush_track_points(self, camera_id: int, stream_info: Dict):
        with stream_info["track_point_lock"]:
            points = stream_info["track_point_buffer"]
            if not points:
                return
            stream_info["track_point_buffer"] = []

        if not settings.BACKEND_ENABLE_CALLBACK:
            return

        try:
            asyncio.run(self._send_track_points_to_backend(points))
        except Exception as e:
            logger.warning(f"Failed to push track points for camera {camera_id}: {e}")
            with stream_info["track_point_lock"]:
                stream_info["track_point_buffer"].extend(points)
                if len(stream_info["track_point_buffer"]) > 500:
                    stream_info["track_point_buffer"] = stream_info["track_point_buffer"][-500:]

    async def _send_track_points_to_backend(self, points: List[Dict]):
        if not points or not settings.BACKEND_TRACK_POINT_URL:
            return

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.post(
                    settings.BACKEND_TRACK_POINT_URL,
                    json=points
                )
                if resp.status_code == 200:
                    result = resp.json()
                    if result.get("code") == 200:
                        logger.debug(f"Pushed {len(points)} track points to backend")
                        return
                logger.warning(f"Track point push failed: {resp.status_code} {resp.text[:200]}")
        except Exception as e:
            logger.warning(f"Track point push exception: {e}")
            raise

    async def _push_detections(self, camera_id: int, tracked_objects, frame: np.ndarray):
        if not settings.DETECTION_PUSH_ENABLED or not tracked_objects:
            return

        try:
            detections_payload = []
            for obj in tracked_objects[:50]:
                detections_payload.append({
                    "trackId": obj.track_id,
                    "classId": obj.class_id,
                    "className": obj.class_name,
                    "confidence": float(obj.confidence),
                    "bbox": {
                        "x1": float(obj.bbox.x1),
                        "y1": float(obj.bbox.y1),
                        "x2": float(obj.bbox.x2),
                        "y2": float(obj.bbox.y2),
                    },
                    "velocity": obj.velocity,
                })

            payload = {
                "cameraId": camera_id,
                "timestamp": datetime.now().isoformat(),
                "frameWidth": frame.shape[1],
                "frameHeight": frame.shape[0],
                "detections": detections_payload,
            }

            async with httpx.AsyncClient(timeout=5.0) as client:
                await client.post(
                    settings.BACKEND_DETECTION_PUSH_URL,
                    json=payload
                )
        except Exception as e:
            pass

    async def _send_event_callback(self, camera_id: int, event, frame: Optional[np.ndarray] = None, event_video_url: Optional[str] = None):
        if not settings.BACKEND_ENABLE_CALLBACK:
            return

        try:
            snapshot_base64 = None
            if frame is not None:
                import cv2
                _, buffer = cv2.imencode(".jpg", frame)
                snapshot_base64 = base64.b64encode(buffer).decode("utf-8")

            event_no = f"EVT{datetime.now().strftime('%Y%m%d%H%M%S')}{camera_id:04d}{uuid.uuid4().hex[:4]}"

            callback_data = AiEventCallbackRequest(
                eventNo=event_no,
                cameraId=camera_id,
                eventType=event.event_type.value,
                eventLevel=event.severity.value,
                confidence=event.confidence,
                eventTime=event.timestamp,
                description=event.description,
                snapshotBase64=snapshot_base64,
                eventVideo=event_video_url,
                trackData=[obj.model_dump() for obj in event.involved_objects] if event.involved_objects else None,
                bbox=event.involved_objects[0].bbox.model_dump() if event.involved_objects else None
            )

            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.post(
                    settings.BACKEND_CALLBACK_URL,
                    json=callback_data.model_dump(mode="json")
                )
                logger.info(
                    f"Event callback sent: camera={camera_id}, type={event.event_type.value}, "
                    f"video={event_video_url is not None}, status={response.status_code}"
                )
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
                        "fps": info["fps"],
                        "bufferSize": len(info["frame_buffer"].buffer),
                        "isRecording": info.get("is_recording", False),
                    }
                return {"cameraId": camera_id, "running": False}

            return {
                "activeCount": len(self.active_streams),
                "streams": [
                    {
                        "cameraId": cid,
                        "running": info["running"],
                        "frameCount": info["frame_count"],
                        "startTime": datetime.fromtimestamp(info["start_time"]).isoformat(),
                        "bufferSize": len(info["frame_buffer"].buffer),
                    }
                    for cid, info in self.active_streams.items()
                ]
            }


stream_processor = StreamProcessor()

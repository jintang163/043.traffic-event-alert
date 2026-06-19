import logging
import os
import threading
import time
from typing import Any, Callable, Dict, List, Optional

logger = logging.getLogger(__name__)

try:
    import cv2
except ImportError:
    cv2 = None


class StreamCapture:
    def __init__(
        self,
        stream_url: str,
        camera_id: int = 0,
        camera_name: str = "",
        reconnect_interval: int = 5,
        fps: float = 25.0,
        on_frame_callback: Optional[Callable[[int, Any, float], None]] = None,
    ):
        self.stream_url = stream_url
        self.camera_id = camera_id
        self.camera_name = camera_name
        self.reconnect_interval = reconnect_interval
        self.fps = fps
        self._on_frame_callback = on_frame_callback
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._cap: Optional[Any] = None
        self._frame_count = 0
        self._lock = threading.Lock()
        self._connected = False

    def is_connected(self) -> bool:
        with self._lock:
            return self._connected

    def _open_stream(self) -> bool:
        if cv2 is None:
            logger.error("opencv-python not installed, cannot open stream")
            return False
        try:
            if self._cap is not None:
                try:
                    self._cap.release()
                except Exception:
                    pass
            self._cap = cv2.VideoCapture(self.stream_url, cv2.CAP_FFMPEG)
            if not self._cap.isOpened():
                os.environ["OPENCV_FFMPEG_CAPTURE_OPTIONS"] = "rtsp_transport;tcp"
                self._cap = cv2.VideoCapture(self.stream_url, cv2.CAP_FFMPEG)
            if self._cap.isOpened():
                self._cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
                logger.info(
                    f"Stream opened: camera={self.camera_name}({self.camera_id}), "
                    f"url={self.stream_url}"
                )
                return True
            else:
                logger.warning(f"Failed to open stream: camera={self.camera_name}")
                return False
        except Exception as e:
            logger.error(f"Error opening stream: {e}")
            return False

    def read_frame(self):
        if self._cap is None or not self._cap.isOpened():
            return None
        try:
            ret, frame = self._cap.read()
            if ret and frame is not None:
                return frame
        except Exception as e:
            logger.debug(f"Frame read error: {e}")
        return None

    def capture_snapshot(self, frame=None, save_path: Optional[str] = None) -> Optional[str]:
        if frame is None:
            frame = self.read_frame()
        if frame is None:
            return None
        if save_path is None:
            return None
        try:
            os.makedirs(os.path.dirname(save_path) or ".", exist_ok=True)
            cv2.imwrite(save_path, frame, [cv2.IMWRITE_JPEG_QUALITY, 90])
            logger.debug(f"Snapshot saved: {save_path}")
            return save_path
        except Exception as e:
            logger.error(f"Failed to save snapshot: {e}")
            return None

    def start(self):
        with self._lock:
            if self._running:
                logger.warning("StreamCapture already running")
                return
            self._running = True
            self._thread = threading.Thread(
                target=self._capture_loop,
                daemon=True,
            )
            self._thread.start()
            logger.info(
                f"StreamCapture started: camera={self.camera_name}({self.camera_id})"
            )

    def stop(self):
        with self._lock:
            self._running = False
        if self._thread:
            self._thread.join(timeout=10)
            self._thread = None
        if self._cap is not None:
            try:
                self._cap.release()
            except Exception:
                pass
            self._cap = None
        self._connected = False
        logger.info(f"StreamCapture stopped: camera={self.camera_name}")

    def _capture_loop(self):
        frame_interval = 1.0 / self.fps if self.fps > 0 else 0.04
        while True:
            with self._lock:
                if not self._running:
                    break

            if not self._connected:
                if not self._open_stream():
                    with self._lock:
                        if not self._running:
                            break
                    time.sleep(self.reconnect_interval)
                    continue
                with self._lock:
                    self._connected = True

            frame = self.read_frame()
            if frame is None:
                with self._lock:
                    self._connected = False
                logger.warning(
                    f"Stream disconnected: camera={self.camera_name}, reconnecting..."
                )
                time.sleep(self.reconnect_interval)
                continue

            self._frame_count += 1
            frame_ts = time.time()

            if self._on_frame_callback:
                try:
                    self._on_frame_callback(self._frame_count, frame, frame_ts)
                except Exception as e:
                    logger.error(f"Frame callback error: {e}")

            with self._lock:
                if not self._running:
                    break
            time.sleep(frame_interval)

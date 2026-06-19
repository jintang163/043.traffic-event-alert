import logging
import threading
import time
from typing import Callable, Optional
from urllib.parse import urlparse

import requests

logger = logging.getLogger(__name__)


class NetworkMonitor:
    def __init__(
        self,
        server_url: str,
        check_interval: int = 10,
        timeout: int = 5,
    ):
        self.server_url = server_url
        self.check_interval = check_interval
        self.timeout = timeout
        self._is_online = False
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._lock = threading.Lock()
        self._callback: Optional[Callable[[bool], None]] = None
        self._health_url = self._get_health_url()

    def _get_health_url(self) -> str:
        try:
            parsed = urlparse(self.server_url)
            return f"{parsed.scheme}://{parsed.netloc}/health"
        except Exception:
            return f"{self.server_url.rstrip('/')}/health"

    def is_online(self) -> bool:
        with self._lock:
            return self._is_online

    def _check_connectivity(self) -> bool:
        try:
            response = requests.get(self._health_url, timeout=self.timeout)
            if response.status_code < 500:
                return True
        except requests.exceptions.Timeout:
            logger.debug(f"Health check timed out: {self._health_url}")
        except requests.exceptions.ConnectionError:
            logger.debug(f"Health check connection error: {self._health_url}")
        except Exception as e:
            logger.debug(f"Health check exception: {e}")

        try:
            response = requests.get(self.server_url, timeout=self.timeout)
            return response.status_code < 500
        except Exception as e:
            logger.debug(f"Server connectivity check failed: {e}")
            return False

    def start_monitoring(self, callback: Callable[[bool], None]):
        with self._lock:
            if self._running:
                logger.warning("Network monitor already running")
                return
            self._callback = callback
            self._running = True
            self._thread = threading.Thread(
                target=self._monitor_loop,
                daemon=True,
            )
            self._thread.start()
            logger.info(
                f"Network monitor started: server={self.server_url}, "
                f"interval={self.check_interval}s"
            )

    def stop(self):
        with self._lock:
            self._running = False
        if self._thread:
            self._thread.join(timeout=5)
            self._thread = None
        logger.info("Network monitor stopped")

    def _monitor_loop(self):
        while True:
            with self._lock:
                if not self._running:
                    break

            current_status = self._check_connectivity()
            with self._lock:
                status_changed = current_status != self._is_online
                self._is_online = current_status

            if status_changed and self._callback:
                try:
                    self._callback(current_status)
                except Exception as e:
                    logger.error(f"Network status callback error: {e}", exc_info=True)

            if current_status:
                logger.debug("Network status: online")
            else:
                logger.debug("Network status: offline")

            with self._lock:
                if not self._running:
                    break
            time.sleep(self.check_interval)

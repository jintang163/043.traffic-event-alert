import json
import logging
import threading
import uuid
from datetime import datetime
from typing import Any, Dict, Optional

from edge_sdk.cache import EventCache
from edge_sdk.config import EdgeNodeConfig
from edge_sdk.heartbeat import HeartbeatManager
from edge_sdk.network_monitor import NetworkMonitor
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
        self._lock = threading.Lock()
        self._started = False

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

            self.network_monitor.start_monitoring(self._on_network_status_change)
            self.uploader.start()
            self.heartbeat_mgr.start()

            self._started = True
            logger.info("EdgeNodeClient started successfully")

    def stop(self):
        with self._lock:
            if not self._started:
                logger.warning("EdgeNodeClient not running")
                return

            logger.info("Stopping EdgeNodeClient...")

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
        return {
            "node_code": self.config.node_code,
            "node_name": self.config.node_name,
            "server_url": self.config.server_url,
            "is_online": self.network_monitor.is_online(),
            "event_queue_size": self.cache.get_queue_size(),
            "started": self._started,
        }

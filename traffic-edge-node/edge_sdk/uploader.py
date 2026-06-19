import json
import logging
import os
import threading
import time
from typing import Any, Callable, Dict, Optional

import requests

logger = logging.getLogger(__name__)


class EventUploader:
    def __init__(
        self,
        server_url: str,
        node_code: str,
        cache=None,
        retry_max: int = 5,
        retry_initial_delay: int = 10,
        is_online_callback: Optional[Callable[[], bool]] = None,
    ):
        self.server_url = server_url.rstrip("/")
        self.node_code = node_code
        self.cache = cache
        self.retry_max = retry_max
        self.retry_initial_delay = retry_initial_delay
        self._is_online_callback = is_online_callback
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._lock = threading.Lock()
        self._wake_event = threading.Event()
        self._register_url = f"{self.server_url}/api/edge/register"
        self._heartbeat_url = f"{self.server_url}/api/edge/heartbeat"
        self._event_upload_url = f"{self.server_url}/api/edge/event/upload"
        self._batch_upload_url = f"{self.server_url}/api/edge/events/batch-upload"
        self._config_url_template = f"{self.server_url}/api/edge/{{nodeCode}}/config"
        self._ack_url_template = f"{self.server_url}/api/edge/{{nodeCode}}/event-ack"

    def _is_online(self) -> bool:
        if self._is_online_callback:
            try:
                return self._is_online_callback()
            except Exception as e:
                logger.debug(f"is_online callback error: {e}")
        return True

    def upload_event(
        self,
        event_data: Dict[str, Any],
        snapshot_file: Optional[str] = None,
        video_file: Optional[str] = None,
    ) -> bool:
        try:
            payload = {
                "nodeCode": self.node_code,
                "eventUuid": event_data.get("event_uuid"),
                "eventType": event_data.get("event_type"),
                "eventTime": event_data.get("event_time"),
                "eventData": event_data.get("event_data"),
            }

            files = {}
            if snapshot_file and os.path.exists(snapshot_file):
                try:
                    files["snapshot"] = (
                        os.path.basename(snapshot_file),
                        open(snapshot_file, "rb"),
                        "image/jpeg",
                    )
                except Exception as e:
                    logger.warning(f"Failed to open snapshot file {snapshot_file}: {e}")

            if video_file and os.path.exists(video_file):
                try:
                    files["video"] = (
                        os.path.basename(video_file),
                        open(video_file, "rb"),
                        "video/mp4",
                    )
                except Exception as e:
                    logger.warning(f"Failed to open video file {video_file}: {e}")

            try:
                if files:
                    response = requests.post(
                        self._event_upload_url,
                        data={"data": json.dumps(payload)},
                        files=files,
                        timeout=30,
                    )
                else:
                    response = requests.post(
                        self._event_upload_url,
                        json=payload,
                        timeout=10,
                    )

                if response.status_code == 200:
                    try:
                        result = response.json()
                        if result.get("code") == 200 or result.get("success"):
                            logger.info(
                                f"Event upload success: uuid={event_data.get('event_uuid')}, "
                                f"type={event_data.get('event_type')}"
                            )
                            return True
                        else:
                            logger.warning(
                                f"Event upload failed: uuid={event_data.get('event_uuid')}, "
                                f"response={response.text[:200]}"
                            )
                            return False
                    except Exception:
                        return True
                else:
                    logger.warning(
                        f"Event upload HTTP error: uuid={event_data.get('event_uuid')}, "
                        f"status={response.status_code}, body={response.text[:200]}"
                    )
                    return False
            finally:
                for f in files.values():
                    try:
                        f[1].close()
                    except Exception:
                        pass
        except requests.exceptions.Timeout:
            logger.warning(f"Event upload timeout: uuid={event_data.get('event_uuid')}")
            return False
        except requests.exceptions.ConnectionError:
            logger.warning(f"Event upload connection error: uuid={event_data.get('event_uuid')}")
            return False
        except Exception as e:
            logger.error(
                f"Event upload exception: uuid={event_data.get('event_uuid')}, error: {e}",
                exc_info=True,
            )
            return False

    def batch_upload_events(
        self,
        events: list,
    ) -> Dict[str, int]:
        success_count = 0
        fail_count = 0
        for item in events:
            event_data = {
                "event_uuid": item.get("event_uuid"),
                "event_type": item.get("event_type"),
                "event_time": item.get("event_time"),
                "event_data": item.get("event_data"),
            }
            ok = self.upload_event(
                event_data,
                snapshot_file=item.get("snapshot_path"),
                video_file=item.get("video_path"),
            )
            if ok:
                success_count += 1
            else:
                fail_count += 1
        return {"success": success_count, "fail": fail_count}

    def send_event_ack(self, event_uuids: list) -> bool:
        try:
            url = self._ack_url_template.format(nodeCode=self.node_code)
            response = requests.post(
                url,
                json={"nodeCode": self.node_code, "eventUuids": event_uuids},
                timeout=10,
            )
            if response.status_code == 200:
                logger.info(f"Event ack success: count={len(event_uuids)}")
                return True
            return False
        except Exception as e:
            logger.warning(f"Event ack failed: {e}")
            return False

    def register_node(self, node_name: str = "") -> bool:
        try:
            payload = {
                "nodeCode": self.node_code,
                "nodeName": node_name,
                "hardwareModel": getattr(self, "_hardware_model", ""),
                "gpuInfo": getattr(self, "_gpu_info", ""),
                "heartbeatInterval": getattr(self, "_heartbeat_interval", 30),
            }
            response = requests.post(
                self._register_url,
                json=payload,
                timeout=10,
            )
            if response.status_code == 200:
                logger.info(f"Node registered successfully: {self.node_code}")
                return True
            else:
                logger.warning(
                    f"Node registration failed: status={response.status_code}, body={response.text[:200]}"
                )
                return False
        except Exception as e:
            logger.error(f"Node registration exception: {e}", exc_info=True)
            return False

    def send_heartbeat(self, heartbeat_data: Dict[str, Any]) -> bool:
        try:
            payload = {
                "nodeCode": heartbeat_data.get("nodeCode", self.node_code),
                "cpuUsage": heartbeat_data.get("cpu_percent"),
                "memoryUsage": heartbeat_data.get("memory_percent"),
                "gpuUsage": heartbeat_data.get("gpu_percent"),
                "temperature": heartbeat_data.get("cpu_temp_c"),
                "networkStatus": 1 if heartbeat_data.get("networkStatus") == "online" else 0,
                "diskUsage": heartbeat_data.get("disk_percent"),
                "processCount": heartbeat_data.get("process_count"),
                "cameraOnlineCount": heartbeat_data.get("camera_online_count"),
                "eventQueueSize": heartbeat_data.get("eventQueueSize", 0),
                "eventCountToday": heartbeat_data.get("event_count_today"),
            }
            response = requests.post(
                self._heartbeat_url,
                json=payload,
                timeout=5,
            )
            if response.status_code == 200:
                return True
            else:
                logger.debug(
                    f"Heartbeat HTTP error: status={response.status_code}"
                )
                return False
        except requests.exceptions.Timeout:
            logger.debug("Heartbeat timeout")
            return False
        except requests.exceptions.ConnectionError:
            logger.debug("Heartbeat connection error")
            return False
        except Exception as e:
            logger.debug(f"Heartbeat exception: {e}")
            return False

    def fetch_config(self) -> Optional[Dict[str, Any]]:
        try:
            url = self._config_url_template.format(nodeCode=self.node_code)
            response = requests.get(url, timeout=10)
            if response.status_code == 200:
                result = response.json()
                return result.get("data", result)
            return None
        except Exception as e:
            logger.debug(f"Fetch config failed: {e}")
            return None

    def process_pending_events(self):
        if not self.cache:
            logger.warning("No cache configured, cannot process pending events")
            return

        try:
            self.cache.reset_failed_events(max_retry=self.retry_max)
        except Exception as e:
            logger.warning(f"Failed to reset failed events: {e}")

        processed_count = 0
        ack_uuids = []
        while True:
            if not self._running:
                break
            if not self._is_online():
                logger.debug("Network offline, stopping pending event processing")
                break

            try:
                events = self.cache.get_pending_events(limit=50)
            except Exception as e:
                logger.error(f"Failed to get pending events: {e}")
                break

            if not events:
                logger.debug("No more pending events in cache")
                break

            for event in events:
                if not self._running:
                    break
                if not self._is_online():
                    break

                event_uuid = event.get("event_uuid")
                if not event_uuid:
                    continue

                if not self.cache.mark_uploading(event_uuid):
                    continue

                event_payload = {
                    "event_uuid": event_uuid,
                    "event_type": event.get("event_type"),
                    "event_time": event.get("event_time"),
                    "event_data": event.get("event_data"),
                }

                success = self.upload_event(
                    event_payload,
                    snapshot_file=event.get("snapshot_path"),
                    video_file=event.get("video_path"),
                )

                if success:
                    self.cache.mark_success(event_uuid)
                    ack_uuids.append(event_uuid)
                    processed_count += 1
                else:
                    retry_count = event.get("retry_count", 0) + 1
                    error_msg = f"Upload failed, retry {retry_count}/{self.retry_max}"
                    self.cache.mark_failed(event_uuid, error_msg)

                    if retry_count < self.retry_max:
                        delay = self.retry_initial_delay * (2 ** (retry_count - 1))
                        logger.info(
                            f"Event {event_uuid} will be retried after {delay}s "
                            f"(attempt {retry_count}/{self.retry_max})"
                        )

                if len(ack_uuids) >= 20:
                    self.send_event_ack(ack_uuids)
                    ack_uuids = []

            if processed_count > 0 and processed_count % 100 == 0:
                logger.info(f"Processed {processed_count} pending events so far")

        if ack_uuids:
            self.send_event_ack(ack_uuids)

        if processed_count > 0:
            logger.info(f"Pending event processing completed: {processed_count} events uploaded")

    def trigger_upload(self):
        self._wake_event.set()

    def start(self):
        with self._lock:
            if self._running:
                logger.warning("Event uploader already running")
                return
            self._running = True
            self._thread = threading.Thread(
                target=self._upload_loop,
                daemon=True,
            )
            self._thread.start()
            logger.info("Event uploader started")

    def stop(self):
        with self._lock:
            self._running = False
        self._wake_event.set()
        if self._thread:
            self._thread.join(timeout=10)
            self._thread = None
        logger.info("Event uploader stopped")

    def _upload_loop(self):
        while True:
            with self._lock:
                if not self._running:
                    break

            if self._is_online():
                try:
                    self.process_pending_events()
                except Exception as e:
                    logger.error(f"Error processing pending events: {e}", exc_info=True)

            self._wake_event.wait(timeout=5)
            self._wake_event.clear()

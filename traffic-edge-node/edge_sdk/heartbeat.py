import logging
import threading
import time
from datetime import datetime
from typing import Any, Callable, Dict, Optional

try:
    import psutil
except ImportError:
    psutil = None

logger = logging.getLogger(__name__)


class HeartbeatManager:
    def __init__(
        self,
        interval: int = 30,
        node_code: str = "",
        node_name: str = "",
        send_callback: Optional[Callable[[Dict[str, Any]], bool]] = None,
        get_queue_size_callback: Optional[Callable[[], int]] = None,
        is_online_callback: Optional[Callable[[], bool]] = None,
    ):
        self.interval = interval
        self.node_code = node_code
        self.node_name = node_name
        self._send_callback = send_callback
        self._get_queue_size_callback = get_queue_size_callback
        self._is_online_callback = is_online_callback
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._lock = threading.Lock()

    def _collect_system_metrics(self) -> Dict[str, Any]:
        metrics: Dict[str, Any] = {
            "cpu_percent": 0.0,
            "memory_percent": 0.0,
            "memory_used_mb": 0.0,
            "memory_total_mb": 0.0,
            "gpu_percent": 0.0,
            "gpu_memory_percent": 0.0,
            "cpu_temp_c": 0.0,
            "disk_percent": 0.0,
        }

        if psutil is None:
            return metrics

        try:
            metrics["cpu_percent"] = psutil.cpu_percent(interval=1)
        except Exception as e:
            logger.debug(f"Failed to get CPU percent: {e}")

        try:
            mem = psutil.virtual_memory()
            metrics["memory_percent"] = mem.percent
            metrics["memory_used_mb"] = round(mem.used / (1024 * 1024), 2)
            metrics["memory_total_mb"] = round(mem.total / (1024 * 1024), 2)
        except Exception as e:
            logger.debug(f"Failed to get memory info: {e}")

        try:
            disk = psutil.disk_usage("/")
            metrics["disk_percent"] = disk.percent
        except Exception as e:
            logger.debug(f"Failed to get disk usage: {e}")

        try:
            if hasattr(psutil, "sensors_temperatures"):
                temps = psutil.sensors_temperatures()
                if temps:
                    for name, entries in temps.items():
                        if "core" in name.lower() or "cpu" in name.lower():
                            if entries:
                                metrics["cpu_temp_c"] = entries[0].current
                                break
        except Exception as e:
            logger.debug(f"Failed to get CPU temperature: {e}")

        try:
            gpu_info = self._get_gpu_info()
            metrics.update(gpu_info)
        except Exception as e:
            logger.debug(f"Failed to get GPU info: {e}")

        return metrics

    def _get_gpu_info(self) -> Dict[str, Any]:
        info = {"gpu_percent": 0.0, "gpu_memory_percent": 0.0}
        try:
            import subprocess
            result = subprocess.run(
                ["nvidia-smi", "--query-gpu=utilization.gpu,utilization.memory",
                 "--format=csv,noheader,nounits"],
                capture_output=True,
                text=True,
                timeout=5,
            )
            if result.returncode == 0 and result.stdout.strip():
                parts = result.stdout.strip().split(",")
                if len(parts) >= 2:
                    info["gpu_percent"] = float(parts[0].strip())
                    info["gpu_memory_percent"] = float(parts[1].strip())
        except Exception:
            pass
        return info

    def _build_heartbeat_data(self) -> Dict[str, Any]:
        metrics = self._collect_system_metrics()
        event_queue_size = 0
        if self._get_queue_size_callback:
            try:
                event_queue_size = self._get_queue_size_callback()
            except Exception as e:
                logger.debug(f"Failed to get queue size: {e}")

        network_status = "offline"
        if self._is_online_callback:
            try:
                network_status = "online" if self._is_online_callback() else "offline"
            except Exception as e:
                logger.debug(f"Failed to get network status: {e}")

        return {
            "nodeCode": self.node_code,
            "nodeName": self.node_name,
            "timestamp": datetime.now().isoformat(),
            "networkStatus": network_status,
            "eventQueueSize": event_queue_size,
            **metrics,
        }

    def send_heartbeat(self) -> bool:
        if not self._send_callback:
            logger.warning("No send callback registered for heartbeat")
            return False

        try:
            data = self._build_heartbeat_data()
            success = self._send_callback(data)
            if success:
                logger.debug(
                    f"Heartbeat sent: node={self.node_code}, "
                    f"queue_size={data.get('eventQueueSize', 0)}, "
                    f"cpu={data.get('cpu_percent', 0)}%"
                )
            else:
                logger.warning(f"Heartbeat send failed for node={self.node_code}")
            return success
        except Exception as e:
            logger.error(f"Heartbeat send exception: {e}", exc_info=True)
            return False

    def start(self):
        with self._lock:
            if self._running:
                logger.warning("Heartbeat manager already running")
                return
            self._running = True
            self._thread = threading.Thread(
                target=self._heartbeat_loop,
                daemon=True,
            )
            self._thread.start()
            logger.info(
                f"Heartbeat manager started: interval={self.interval}s, "
                f"node={self.node_code}"
            )

    def stop(self):
        with self._lock:
            self._running = False
        if self._thread:
            self._thread.join(timeout=5)
            self._thread = None
        logger.info("Heartbeat manager stopped")

    def _heartbeat_loop(self):
        while True:
            with self._lock:
                if not self._running:
                    break
            self.send_heartbeat()
            for _ in range(self.interval * 10):
                with self._lock:
                    if not self._running:
                        return
                time.sleep(0.1)

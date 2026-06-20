import json
import logging
import time
from typing import Dict, List, Optional, Tuple
from threading import RLock

import numpy as np

from app.schemas.tracking import TrackedObject

logger = logging.getLogger(__name__)


class ConeDetector:
    def __init__(self):
        self._lock = RLock()
        self._cone_configs: Dict[str, dict] = {}
        self._camera_cones: Dict[str, List[dict]] = {}
        self._last_detection_times: Dict[str, float] = {}
        self._detection_interval = 30.0

        self._cone_class_ids = {11, 12}
        self._cone_class_names = {"traffic cone", "cone", "bollard", "barrel"}

    def set_cone_config(self, camera_id: str, config: dict):
        with self._lock:
            self._cone_configs[camera_id] = config
            logger.info(f"设置摄像头[{camera_id}]锥桶检测配置: {config}")

    def get_cone_config(self, camera_id: str) -> Optional[dict]:
        with self._lock:
            return self._cone_configs.get(camera_id)

    def remove_cone_config(self, camera_id: str):
        with self._lock:
            self._cone_configs.pop(camera_id, None)
            self._camera_cones.pop(camera_id, None)
            self._last_detection_times.pop(camera_id, None)
            logger.info(f"移除摄像头[{camera_id}]锥桶检测配置")

    def detect_cones(
        self,
        camera_id: str,
        tracked_objects: List[TrackedObject],
        frame_width: int = 1920,
        frame_height: int = 1080,
        construction_zone: Optional[List[Tuple[float, float]]] = None,
    ) -> dict:
        with self._lock:
            current_time = time.time()
            last_time = self._last_detection_times.get(camera_id, 0)

            if current_time - last_time < self._detection_interval:
                cached = self._camera_cones.get(camera_id, [])
                return {
                    "cones": cached,
                    "count": len(cached),
                    "from_cache": True,
                }

            cones = []
            for obj in tracked_objects:
                class_name = obj.class_name.lower()
                if self._is_cone(class_name, obj.class_id):
                    cone_info = {
                        "track_id": obj.track_id,
                        "class_id": obj.class_id,
                        "class_name": obj.class_name,
                        "confidence": obj.confidence,
                        "bbox": {
                            "x1": obj.bbox.x1,
                            "y1": obj.bbox.y1,
                            "x2": obj.bbox.x2,
                            "y2": obj.bbox.y2,
                        },
                        "center_x": (obj.bbox.x1 + obj.bbox.x2) / 2,
                        "center_y": (obj.bbox.y1 + obj.bbox.y2) / 2,
                        "pixel_x": (obj.bbox.x1 + obj.bbox.x2) / 2 / frame_width,
                        "pixel_y": (obj.bbox.y1 + obj.bbox.y2) / 2 / frame_height,
                    }
                    cones.append(cone_info)

            if construction_zone and len(construction_zone) >= 3:
                cones = [
                    c for c in cones
                    if self._is_point_in_polygon(
                        c["center_x"], c["center_y"], construction_zone
                    )
                ]

            self._camera_cones[camera_id] = cones
            self._last_detection_times[camera_id] = current_time

            logger.debug(
                f"锥桶检测: camera={camera_id}, 检测到 {len(cones)} 个锥桶"
            )

            return {
                "cones": cones,
                "count": len(cones),
                "from_cache": False,
                "detection_time": current_time,
            }

    def check_cone_compliance(
        self,
        camera_id: str,
        tracked_objects: List[TrackedObject],
        standard_count: int,
        frame_width: int = 1920,
        frame_height: int = 1080,
        construction_zone: Optional[List[Tuple[float, float]]] = None,
        tolerance_ratio: float = 0.9,
    ) -> dict:
        detection_result = self.detect_cones(
            camera_id, tracked_objects, frame_width, frame_height, construction_zone
        )
        cones = detection_result["cones"]
        detected_count = len(cones)

        missing_count = max(0, standard_count - detected_count)
        extra_count = max(0, detected_count - standard_count)

        if standard_count > 0:
            compliance_rate = min(100.0, detected_count / standard_count * 100)
        else:
            compliance_rate = 100.0

        is_compliant = detected_count >= standard_count * tolerance_ratio and extra_count <= standard_count * 0.1

        avg_confidence = 0.0
        min_confidence = 0.0
        if cones:
            confidences = [c["confidence"] for c in cones]
            avg_confidence = sum(confidences) / len(confidences)
            min_confidence = min(confidences)

        result = {
            "detected_count": detected_count,
            "standard_count": standard_count,
            "missing_count": missing_count,
            "extra_count": extra_count,
            "compliance_rate": round(compliance_rate, 2),
            "is_compliant": is_compliant,
            "avg_confidence": round(avg_confidence, 4),
            "min_confidence": round(min_confidence, 4),
            "cones": cones,
            "alert_level": self._calculate_alert_level(missing_count, standard_count),
        }

        if not is_compliant:
            logger.warning(
                f"锥桶摆放不合规: camera={camera_id}, "
                f"标准={standard_count}, 检测={detected_count}, "
                f"缺失={missing_count}, 合规率={compliance_rate:.1f}%"
            )

        return result

    def estimate_cone_positions(
        self,
        cones: List[dict],
        camera_lng: float,
        camera_lat: float,
        frame_width: int = 1920,
        frame_height: int = 1080,
    ) -> List[dict]:
        result = []
        for cone in cones:
            pixel_x = cone.get("pixel_x", 0.5)
            pixel_y = cone.get("pixel_y", 0.5)

            lng_offset = (pixel_x - 0.5) * 0.001
            lat_offset = (1 - pixel_y) * 0.0005

            gis_cone = {
                **cone,
                "longitude": round(camera_lng + lng_offset, 6),
                "latitude": round(camera_lat + lat_offset, 6),
            }
            result.append(gis_cone)

        return result

    def _is_cone(self, class_name: str, class_id: int) -> bool:
        if class_id in self._cone_class_ids:
            return True

        name_lower = class_name.lower()
        for cone_name in self._cone_class_names:
            if cone_name in name_lower:
                return True

        return False

    def _is_point_in_polygon(self, x: float, y: float, polygon: List[Tuple[float, float]]) -> bool:
        n = len(polygon)
        if n < 3:
            return False

        inside = False
        j = n - 1
        for i in range(n):
            xi, yi = polygon[i]
            xj, yj = polygon[j]

            if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi) + xi):
                inside = not inside
            j = i

        return inside

    def _calculate_alert_level(self, missing_count: int, standard_count: int) -> int:
        if missing_count == 0:
            return 1

        if standard_count == 0:
            return 1

        missing_ratio = missing_count / standard_count

        if missing_ratio >= 0.3:
            return 3
        elif missing_ratio >= 0.15:
            return 2
        else:
            return 1

    def clear_expired(self, max_age: float = 300.0):
        current_time = time.time()
        with self._lock:
            expired = [
                cam_id for cam_id, last_time in self._last_detection_times.items()
                if current_time - last_time > max_age
            ]
            for cam_id in expired:
                self._camera_cones.pop(cam_id, None)
                self._last_detection_times.pop(cam_id, None)
                logger.debug(f"清理过期锥桶检测缓存: camera={cam_id}")

    def load_configs(self, config_list: List[dict]):
        with self._lock:
            self._cone_configs.clear()
            for config in config_list:
                camera_id = str(config.get("camera_id", config.get("cameraId", "")))
                if camera_id:
                    self._cone_configs[camera_id] = config
            logger.info(f"从列表加载了 {len(self._cone_configs)} 个锥桶检测配置")


cone_detector = ConeDetector()

import logging
import time
from typing import Dict, List, Optional
from threading import RLock

from app.schemas.fence import FenceConfig
from app.schemas.tracking import TrackedObject
from app.utils.polygon import (
    GeoPoint,
    geo_point_in_polygon,
    bbox_in_polygon,
    bbox_overlap_polygon_ratio,
    parse_polygon_points,
    Point,
)

logger = logging.getLogger(__name__)


class FenceManager:
    def __init__(self):
        self._fences: Dict[str, FenceConfig] = {}
        self._camera_fences: Dict[int, List[str]] = {}
        self._lock = RLock()
        self._intrusion_states: Dict[str, Dict[int, float]] = {}
        self._last_alert_times: Dict[str, Dict[str, float]] = {}

    def add_fence(self, fence: FenceConfig):
        with self._lock:
            self._fences[fence.fence_id] = fence
            if fence.camera_id:
                if fence.camera_id not in self._camera_fences:
                    self._camera_fences[fence.camera_id] = []
                if fence.fence_id not in self._camera_fences[fence.camera_id]:
                    self._camera_fences[fence.camera_id].append(fence.fence_id)
            logger.info(f"添加围栏: {fence.fence_name} ({fence.fence_id})")

    def remove_fence(self, fence_id: str):
        with self._lock:
            fence = self._fences.pop(fence_id, None)
            if fence and fence.camera_id:
                if fence.camera_id in self._camera_fences:
                    if fence_id in self._camera_fences[fence.camera_id]:
                        self._camera_fences[fence.camera_id].remove(fence_id)
            self._intrusion_states.pop(fence_id, None)
            self._last_alert_times.pop(fence_id, None)
            if fence:
                logger.info(f"移除围栏: {fence.fence_name} ({fence_id})")

    def update_fence(self, fence: FenceConfig):
        with self._lock:
            old_fence = self._fences.get(fence.fence_id)
            if old_fence and old_fence.camera_id != fence.camera_id:
                if old_fence.camera_id and old_fence.camera_id in self._camera_fences:
                    if fence.fence_id in self._camera_fences[old_fence.camera_id]:
                        self._camera_fences[old_fence.camera_id].remove(fence.fence_id)

            self._fences[fence.fence_id] = fence
            if fence.camera_id:
                if fence.camera_id not in self._camera_fences:
                    self._camera_fences[fence.camera_id] = []
                if fence.fence_id not in self._camera_fences[fence.camera_id]:
                    self._camera_fences[fence.camera_id].append(fence.fence_id)
            logger.info(f"更新围栏: {fence.fence_name} ({fence.fence_id})")

    def get_fence(self, fence_id: str) -> Optional[FenceConfig]:
        with self._lock:
            return self._fences.get(fence_id)

    def get_fences_by_camera(self, camera_id: int) -> List[FenceConfig]:
        with self._lock:
            fence_ids = self._camera_fences.get(camera_id, [])
            return [self._fences[fid] for fid in fence_ids if fid in self._fences]

    def get_all_fences(self) -> List[FenceConfig]:
        with self._lock:
            return list(self._fences.values())

    def check_point_intrusion(self, lng: float, lat: float, camera_id: Optional[int] = None) -> List[FenceConfig]:
        fences = self.get_fences_by_camera(camera_id) if camera_id else self.get_all_fences()
        intruded = []
        point = GeoPoint(lng, lat)

        for fence in fences:
            if not fence.alert_enabled:
                continue
            if not fence.polygon_points or len(fence.polygon_points) < 3:
                continue

            polygon = [GeoPoint(p[0], p[1]) for p in fence.polygon_points]
            if geo_point_in_polygon(point, polygon):
                intruded.append(fence)

        return intruded

    def check_bbox_intrusion(
        self,
        x1: float, y1: float, x2: float, y2: float,
        frame_width: int, frame_height: int,
        camera_id: Optional[int] = None
    ) -> List[tuple]:
        fences = self.get_fences_by_camera(camera_id) if camera_id else self.get_all_fences()
        results = []

        for fence in fences:
            if not fence.alert_enabled:
                continue
            if not fence.polygon_points or len(fence.polygon_points) < 3:
                continue

            norm_polygon = [
                Point(p[0] * frame_width, p[1] * frame_height)
                for p in fence.polygon_points
            ]

            overlap = bbox_overlap_polygon_ratio(x1, y1, x2, y2, norm_polygon)
            if overlap > 0:
                results.append((fence, overlap))

        results.sort(key=lambda x: x[1], reverse=True)
        return results

    def check_tracked_objects(
        self,
        tracked_objects: List[TrackedObject],
        frame_width: int,
        frame_height: int,
        camera_id: Optional[int] = None,
        fps: float = 2.0
    ) -> List[dict]:
        intrusions = []
        current_time = time.time()

        for obj in tracked_objects:
            class_name = obj.class_name.lower()
            x1 = obj.bbox.x1
            y1 = obj.bbox.y1
            x2 = obj.bbox.x2
            y2 = obj.bbox.y2

            fences = self.get_fences_by_camera(camera_id) if camera_id else self.get_all_fences()

            for fence in fences:
                if not fence.alert_enabled:
                    continue
                if not fence.polygon_points or len(fence.polygon_points) < 3:
                    continue

                if fence.detect_target_types:
                    target_lower = [t.lower() for t in fence.detect_target_types]
                    if class_name not in target_lower:
                        continue

                norm_polygon = [
                    Point(p[0] * frame_width, p[1] * frame_height)
                    for p in fence.polygon_points
                ]

                is_inside = bbox_in_polygon(
                    x1, y1, x2, y2, norm_polygon,
                    check_center=True, check_corners=True
                )

                if is_inside:
                    state_key = f"{fence.fence_id}_{obj.track_id}"

                    if fence.fence_id not in self._intrusion_states:
                        self._intrusion_states[fence.fence_id] = {}

                    intrusion_start = self._intrusion_states[fence.fence_id].get(
                        obj.track_id, current_time
                    )

                    if obj.track_id not in self._intrusion_states[fence.fence_id]:
                        self._intrusion_states[fence.fence_id][obj.track_id] = current_time
                        intrusion_start = current_time

                    duration = current_time - intrusion_start

                    should_alert = False
                    if fence.stay_seconds <= 0 or duration >= fence.stay_seconds:
                        last_alert_key = f"{fence.fence_id}_{obj.track_id}"
                        last_alert = self._last_alert_times.get(fence.fence_id, {}).get(
                            last_alert_key, 0
                        )
                        if current_time - last_alert >= fence.cooldown_seconds:
                            should_alert = True
                            if fence.fence_id not in self._last_alert_times:
                                self._last_alert_times[fence.fence_id] = {}
                            self._last_alert_times[fence.fence_id][last_alert_key] = current_time

                    intrusions.append({
                        "fence": fence,
                        "tracked_object": obj,
                        "duration": duration,
                        "should_alert": should_alert,
                    })
                else:
                    if fence.fence_id in self._intrusion_states:
                        self._intrusion_states[fence.fence_id].pop(obj.track_id, None)

        return intrusions

    def clear_expired_states(self, max_age: float = 300.0):
        current_time = time.time()
        with self._lock:
            for fence_id in list(self._intrusion_states.keys()):
                states = self._intrusion_states[fence_id]
                expired = [
                    track_id for track_id, start_time in states.items()
                    if current_time - start_time > max_age
                ]
                for track_id in expired:
                    del states[track_id]

            for fence_id in list(self._last_alert_times.keys()):
                times = self._last_alert_times[fence_id]
                expired = [
                    key for key, last_time in times.items()
                    if current_time - last_time > max_age * 2
                ]
                for key in expired:
                    del times[key]

    def load_from_list(self, fence_list: List[dict]):
        with self._lock:
            self._fences.clear()
            self._camera_fences.clear()
            self._intrusion_states.clear()
            self._last_alert_times.clear()

            for f_data in fence_list:
                try:
                    fence = FenceConfig(
                        fence_id=str(f_data.get("id", "")),
                        fence_code=f_data.get("fenceCode", ""),
                        fence_name=f_data.get("fenceName", ""),
                        fence_type=f_data.get("fenceType", 1),
                        camera_id=f_data.get("cameraId"),
                        polygon_points=f_data.get("polygonPoints", []),
                        center_lng=f_data.get("centerLongitude"),
                        center_lat=f_data.get("centerLatitude"),
                        area=f_data.get("area"),
                        alert_enabled=f_data.get("alertEnabled", 1) == 1,
                        alert_level=f_data.get("alertLevel", 2),
                        detect_target_types=self._parse_target_types(f_data.get("detectTargetTypes", "")),
                        stay_seconds=f_data.get("staySeconds", 0),
                        cooldown_seconds=f_data.get("cooldownSeconds", 60),
                        notify_enabled=f_data.get("notifyEnabled", 1) == 1,
                        link_work_order=f_data.get("linkWorkOrder", 0) == 1,
                        color=f_data.get("color", "#ff4d4f"),
                        description=f_data.get("description"),
                    )
                    self.add_fence(fence)
                except Exception as e:
                    logger.error(f"加载围栏配置失败: {e}")

            logger.info(f"从列表加载了 {len(self._fences)} 个围栏配置")

    def _parse_target_types(self, types_str: str) -> List[str]:
        if not types_str:
            return []
        return [t.strip() for t in types_str.split(",") if t.strip()]


fence_manager = FenceManager()

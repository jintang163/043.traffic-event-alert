import logging
import time
import uuid
from datetime import datetime
from typing import Dict, List, Optional, Tuple

import numpy as np

from app.config.settings import settings
from app.core.feature_extractor import plate_recognizer
from app.core.tracker import get_tracker, Track
from app.core.fence_manager import fence_manager
from app.schemas.event import (
    TrafficEvent, EventType, EventSeverity, EventLocation
)
from app.schemas.tracking import TrackedObject

logger = logging.getLogger(__name__)


class EventAnalyzer:
    def __init__(self):
        self.cooldown_events: Dict[str, float] = {}
        self.cooldown_seconds = 60
        self.pedestrian_intrusion_tracks: Dict[str, Dict[str, float]] = {}
        self.pedestrian_road_regions: Dict[str, List[Tuple[float, float]]] = {}

    def analyze(
        self,
        camera_id: str,
        tracked_objects: List[TrackedObject],
        reference_direction: Optional[float] = None,
        frame_width: int = 1920,
        frame_height: int = 1080,
        frame: Optional[np.ndarray] = None,
    ) -> List[TrafficEvent]:
        events: List[TrafficEvent] = []
        tracker = get_tracker(camera_id)

        accident_events = self._detect_accident(camera_id, tracker, tracked_objects)
        events.extend(accident_events)

        reverse_events = self._detect_reverse(camera_id, tracker, tracked_objects, reference_direction, frame)
        events.extend(reverse_events)

        debris_events = self._detect_debris(camera_id, tracker, tracked_objects, frame_width, frame_height)
        events.extend(debris_events)

        intrusion_events = self._detect_intrusion(camera_id, tracked_objects, frame_width, frame_height)
        events.extend(intrusion_events)

        if settings.EVENT_PEDESTRIAN_INTRUSION_ENABLED:
            pedestrian_intrusion_events = self._detect_pedestrian_intrusion(
                camera_id, tracked_objects, frame_width, frame_height
            )
            events.extend(pedestrian_intrusion_events)

        return events

    def _is_in_cooldown(self, event_key: str) -> bool:
        now = time.time()
        last_time = self.cooldown_events.get(event_key, 0)
        if now - last_time < self.cooldown_seconds:
            return True
        self.cooldown_events[event_key] = now
        return False

    def _detect_accident(
        self,
        camera_id: str,
        tracker,
        tracked_objects: List[TrackedObject]
    ) -> List[TrafficEvent]:
        events: List[TrafficEvent] = []

        vehicle_tracks = [
            tracker.get_track(obj.track_id)
            for obj in tracked_objects
            if obj.class_id in {2, 3, 5, 7}
        ]
        vehicle_tracks = [t for t in vehicle_tracks if t is not None]

        for track in vehicle_tracks:
            if track.is_static(settings.EVENT_ACCIDENT_STATIC_FRAMES) and track.hits > 10:
                event_key = f"accident_{camera_id}_{track.track_id}"
                if not self._is_in_cooldown(event_key):
                    confidence = min(0.95, 0.7 + (track.static_count - settings.EVENT_ACCIDENT_STATIC_FRAMES) * 0.01)
                    if confidence >= settings.EVENT_CONFIDENCE_THRESHOLD:
                        event = self._create_event(
                            camera_id=camera_id,
                            event_type=EventType.ACCIDENT,
                            severity=EventSeverity.HIGH,
                            involved_objects=[track.to_tracked_object()],
                            confidence=confidence,
                            description=f"检测到车辆异常静止，可能发生交通事故，车辆ID: {track.track_id}"
                        )
                        events.append(event)
                        logger.warning(f"Accident detected: camera={camera_id}, track={track.track_id}")

        for i in range(len(vehicle_tracks)):
            for j in range(i + 1, len(vehicle_tracks)):
                t1, t2 = vehicle_tracks[i], vehicle_tracks[j]
                iou = self._track_iou(t1, t2)
                if iou > 0.3 and t1.is_static(10) and t2.is_static(10):
                    event_key = f"accident_collision_{camera_id}_{t1.track_id}_{t2.track_id}"
                    if not self._is_in_cooldown(event_key):
                        event = self._create_event(
                            camera_id=camera_id,
                            event_type=EventType.ACCIDENT,
                            severity=EventSeverity.CRITICAL,
                            involved_objects=[t1.to_tracked_object(), t2.to_tracked_object()],
                            confidence=0.92,
                            description=f"检测到车辆碰撞，车辆ID: {t1.track_id} 和 {t2.track_id}，IOU: {iou:.2f}"
                        )
                        events.append(event)
                        logger.warning(f"Collision detected: camera={camera_id}, tracks={t1.track_id},{t2.track_id}")

        return events

    def _detect_reverse(
        self,
        camera_id: str,
        tracker,
        tracked_objects: List[TrackedObject],
        reference_direction: Optional[float],
        frame: Optional[np.ndarray] = None,
    ) -> List[TrafficEvent]:
        events: List[TrafficEvent] = []
        if reference_direction is None:
            reference_direction = 0.0

        vehicle_objects = [obj for obj in tracked_objects if obj.class_id in {2, 3, 5, 7}]

        for obj in vehicle_objects:
            track = tracker.get_track(obj.track_id)
            if track is None or track.hits < 5 or track.velocity is None:
                continue

            direction = track.get_average_direction()
            if direction is None:
                continue

            speed = np.sqrt(track.velocity[0] ** 2 + track.velocity[1] ** 2)
            if speed < settings.EVENT_REVERSE_MIN_SPEED:
                continue

            direction_diff = abs(direction - reference_direction)
            direction_diff = min(direction_diff, 2 * np.pi - direction_diff)

            if direction_diff > np.pi * 0.6:
                event_key = f"reverse_{camera_id}_{track.track_id}"
                if not self._is_in_cooldown(event_key):
                    confidence = min(0.95, 0.7 + (direction_diff - np.pi * 0.6) / (np.pi * 0.4) * 0.25)
                    if confidence >= settings.EVENT_CONFIDENCE_THRESHOLD:
                        involved = [track.to_tracked_object()]
                        plates = plate_recognizer.recognize_for_reverse_event(involved, frame)
                        event = self._create_event(
                            camera_id=camera_id,
                            event_type=EventType.REVERSE,
                            severity=EventSeverity.MEDIUM,
                            involved_objects=involved,
                            confidence=confidence,
                            description=(
                                f"检测到车辆逆行，车辆ID: {track.track_id}，速度: {speed:.1f}px/frame"
                                + (f"，识别车牌: {plates[0][1].plate_number}" if plates else "")
                            )
                        )
                        if plates:
                            event.license_plates = [p[1] for p in plates]
                            logger.warning(
                                "Reverse driving detected with plate: camera=%s, track=%s, plate=%s, conf=%.3f",
                                camera_id, track.track_id, plates[0][1].plate_number, plates[0][1].confidence,
                            )
                        else:
                            logger.warning(f"Reverse driving detected: camera={camera_id}, track={track.track_id}")
                        events.append(event)

        return events

    def _detect_debris(
        self,
        camera_id: str,
        tracker,
        tracked_objects: List[TrackedObject],
        frame_width: int,
        frame_height: int
    ) -> List[TrafficEvent]:
        events: List[TrafficEvent] = []

        debris_classes = {24, 26, 28, 39, 41, 56, 57, 62, 63, 67, 73}
        debris_objects = [obj for obj in tracked_objects if obj.class_id in debris_classes]

        for obj in debris_objects:
            track = tracker.get_track(obj.track_id)
            if track is None:
                continue

            if track.is_static(settings.EVENT_DEBRIS_STATIC_FRAMES) and track.hits > 5:
                bbox_area = (track.bbox.x2 - track.bbox.x1) * (track.bbox.y2 - track.bbox.y1)
                frame_area = frame_width * frame_height
                area_ratio = bbox_area / frame_area

                if 0.001 < area_ratio < 0.3:
                    event_key = f"debris_{camera_id}_{track.track_id}"
                    if not self._is_in_cooldown(event_key):
                        confidence = min(0.9, 0.7 + (track.static_count - settings.EVENT_DEBRIS_STATIC_FRAMES) * 0.01)
                        if confidence >= settings.EVENT_CONFIDENCE_THRESHOLD:
                            event = self._create_event(
                                camera_id=camera_id,
                                event_type=EventType.DEBRIS,
                                severity=EventSeverity.LOW,
                                involved_objects=[obj],
                                confidence=confidence,
                                description=f"检测到路面抛洒物，类别: {track.class_name}，面积占比: {area_ratio:.4f}"
                            )
                            events.append(event)
                            logger.warning(f"Debris detected: camera={camera_id}, track={track.track_id}, class={track.class_name}")

        return events

    def _detect_intrusion(
        self,
        camera_id: str,
        tracked_objects: List[TrackedObject],
        frame_width: int,
        frame_height: int
    ) -> List[TrafficEvent]:
        events: List[TrafficEvent] = []

        try:
            camera_id_int = int(camera_id) if camera_id and camera_id.isdigit() else None
        except (ValueError, TypeError):
            camera_id_int = None

        intrusions = fence_manager.check_tracked_objects(
            tracked_objects,
            frame_width,
            frame_height,
            camera_id=camera_id_int
        )

        for intrusion in intrusions:
            if not intrusion["should_alert"]:
                continue

            fence = intrusion["fence"]
            obj = intrusion["tracked_object"]
            duration = intrusion["duration"]

            event_key = f"intrusion_{camera_id}_{fence.fence_id}_{obj.track_id}"
            if self._is_in_cooldown(event_key):
                continue

            severity = EventSeverity(fence.alert_level) if fence.alert_level <= 4 else EventSeverity.MEDIUM
            target_type_text = obj.class_name
            fence_type_text = self._get_fence_type_text(fence.fence_type)

            confidence = min(0.95, 0.7 + min(duration, 30) * 0.01)

            event = self._create_event(
                camera_id=camera_id,
                event_type=EventType.INTRUSION,
                severity=severity,
                involved_objects=[obj],
                confidence=confidence,
                description=f"检测到{target_type_text}入侵{fence_type_text}「{fence.fence_name}」，已停留{duration:.1f}秒",
            )
            event.metadata = {
                "fence_id": fence.fence_id,
                "fence_code": fence.fence_code,
                "fence_name": fence.fence_name,
                "fence_type": fence.fence_type,
                "intrusion_duration": duration,
                "track_id": obj.track_id,
                "target_class": obj.class_name,
            }
            events.append(event)
            logger.warning(
                f"区域入侵检测: camera={camera_id}, fence={fence.fence_name}, "
                f"target={obj.class_name}, track={obj.track_id}, duration={duration:.1f}s"
            )

        return events

    def _detect_pedestrian_intrusion(
        self,
        camera_id: str,
        tracked_objects: List[TrackedObject],
        frame_width: int,
        frame_height: int
    ) -> List[TrafficEvent]:
        events: List[TrafficEvent] = []

        pedestrian_objects = [obj for obj in tracked_objects if obj.class_id == 0 or obj.class_name == "person"]
        if not pedestrian_objects:
            self._cleanup_pedestrian_tracks(camera_id, [])
            return events

        road_region = self._get_road_region(camera_id, frame_width, frame_height)

        now = time.time()
        camera_track_ids = set()

        for obj in pedestrian_objects:
            track_id = str(obj.track_id)
            camera_track_ids.add(track_id)

            bbox_center_x = (obj.bbox.x1 + obj.bbox.x2) / 2
            bbox_bottom_y = obj.bbox.y2

            is_in_road = self._is_point_in_polygon(bbox_center_x, bbox_bottom_y, road_region)

            track_key = f"{camera_id}_{track_id}"

            if is_in_road:
                if track_key not in self.pedestrian_intrusion_tracks:
                    self.pedestrian_intrusion_tracks[track_key] = {
                        "enter_time": now,
                        "last_seen": now,
                        "alerted": False
                    }
                else:
                    self.pedestrian_intrusion_tracks[track_key]["last_seen"] = now

                track_info = self.pedestrian_intrusion_tracks[track_key]
                duration = now - track_info["enter_time"]

                if (duration >= settings.EVENT_PEDESTRIAN_INTRUSION_STAY_SECONDS
                        and not track_info["alerted"]):

                    event_key = f"pedestrian_intrusion_{camera_id}_{track_id}"
                    if self._is_in_cooldown(event_key):
                        continue

                    level_value = min(settings.EVENT_PEDESTRIAN_INTRUSION_DEFAULT_LEVEL, 4)
                    severity_level = EventSeverity(level_value)

                    confidence = min(0.95, 0.7 + min(duration, 30) * 0.01)
                    if confidence >= settings.EVENT_CONFIDENCE_THRESHOLD:
                        event = self._create_event(
                            camera_id=camera_id,
                            event_type=EventType.PEDESTRIAN_INTRUSION,
                            severity=severity_level,
                            involved_objects=[obj],
                            confidence=confidence,
                            description=f"检测到行人闯入行车道，行人ID: {track_id}，已停留{duration:.1f}秒"
                        )
                        event.metadata = {
                            "track_id": track_id,
                            "stay_duration": duration,
                            "road_region_type": "default",
                            "led_message": "行人请离开",
                            "intrusion_point": {
                                "x": bbox_center_x,
                                "y": bbox_bottom_y
                            }
                        }
                        events.append(event)
                        track_info["alerted"] = True
                        logger.warning(
                            f"行人闯入行车道: camera={camera_id}, track={track_id}, "
                            f"duration={duration:.1f}s, confidence={confidence:.3f}"
                        )
            else:
                if track_key in self.pedestrian_intrusion_tracks:
                    del self.pedestrian_intrusion_tracks[track_key]

        self._cleanup_pedestrian_tracks(camera_id, camera_track_ids)

        return events

    def _get_road_region(
        self,
        camera_id: str,
        frame_width: int,
        frame_height: int
    ) -> List[Tuple[float, float]]:
        if camera_id in self.pedestrian_road_regions:
            normalized = self.pedestrian_road_regions[camera_id]
            return [(x * frame_width, y * frame_height) for (x, y) in normalized]

        default_region = [
            (0, int(frame_height * 0.35)),
            (frame_width, int(frame_height * 0.35)),
            (frame_width, frame_height),
            (0, frame_height),
        ]
        return default_region

    def get_road_region(self, camera_id: str) -> Optional[List[Tuple[float, float]]]:
        return self.pedestrian_road_regions.get(camera_id)

    def set_road_region(self, camera_id: str, region_points: List[Tuple[float, float]]):
        self.pedestrian_road_regions[camera_id] = region_points
        logger.info(f"已设置摄像头[{camera_id}]行车道区域: {len(region_points)}个顶点 (归一化坐标)")

    def clear_road_region(self, camera_id: str):
        if camera_id in self.pedestrian_road_regions:
            del self.pedestrian_road_regions[camera_id]
            logger.info(f"已清除摄像头[{camera_id}]行车道区域配置，将使用默认区域")

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

    def _cleanup_pedestrian_tracks(self, camera_id: str, active_track_ids: set):
        keys_to_delete = []
        for key in self.pedestrian_intrusion_tracks:
            if key.startswith(f"{camera_id}_"):
                track_id = key.split(f"{camera_id}_")[1]
                if track_id not in active_track_ids:
                    keys_to_delete.append(key)
        for key in keys_to_delete:
            del self.pedestrian_intrusion_tracks[key]

    def _get_fence_type_text(self, fence_type: int) -> str:
        type_map = {
            1: "施工区",
            2: "应急车道",
            3: "禁入区",
            4: "自定义区域",
        }
        return type_map.get(fence_type, "电子围栏")

    def _track_iou(self, t1: Track, t2: Track) -> float:
        x1 = max(t1.bbox.x1, t2.bbox.x1)
        y1 = max(t1.bbox.y1, t2.bbox.y1)
        x2 = min(t1.bbox.x2, t2.bbox.x2)
        y2 = min(t1.bbox.y2, t2.bbox.y2)
        if x2 <= x1 or y2 <= y1:
            return 0.0
        intersection = (x2 - x1) * (y2 - y1)
        area1 = (t1.bbox.x2 - t1.bbox.x1) * (t1.bbox.y2 - t1.bbox.y1)
        area2 = (t2.bbox.x2 - t2.bbox.x1) * (t2.bbox.y2 - t2.bbox.y1)
        union = area1 + area2 - intersection
        return intersection / union if union > 0 else 0.0

    def _create_event(
        self,
        camera_id: str,
        event_type: EventType,
        severity: EventSeverity,
        involved_objects: List[TrackedObject],
        confidence: float,
        description: str
    ) -> TrafficEvent:
        return TrafficEvent(
            event_id=str(uuid.uuid4()),
            event_type=event_type,
            severity=severity,
            timestamp=datetime.now(),
            location=EventLocation(camera_id=str(camera_id)),
            description=description,
            involved_objects=involved_objects,
            confidence=round(confidence, 4)
        )


event_analyzer = EventAnalyzer()

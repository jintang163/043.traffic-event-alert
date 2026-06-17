import logging
import time
import uuid
from datetime import datetime
from typing import Dict, List, Optional, Tuple

import numpy as np

from app.config.settings import settings
from app.core.tracker import get_tracker, Track
from app.schemas.event import (
    TrafficEvent, EventType, EventSeverity, EventLocation
)
from app.schemas.tracking import TrackedObject

logger = logging.getLogger(__name__)


class EventAnalyzer:
    def __init__(self):
        self.cooldown_events: Dict[str, float] = {}
        self.cooldown_seconds = 60

    def analyze(
        self,
        camera_id: str,
        tracked_objects: List[TrackedObject],
        reference_direction: Optional[float] = None,
        frame_width: int = 1920,
        frame_height: int = 1080
    ) -> List[TrafficEvent]:
        events: List[TrafficEvent] = []
        tracker = get_tracker(camera_id)

        accident_events = self._detect_accident(camera_id, tracker, tracked_objects)
        events.extend(accident_events)

        reverse_events = self._detect_reverse(camera_id, tracker, tracked_objects, reference_direction)
        events.extend(reverse_events)

        debris_events = self._detect_debris(camera_id, tracker, tracked_objects, frame_width, frame_height)
        events.extend(debris_events)

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
        reference_direction: Optional[float]
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
                        event = self._create_event(
                            camera_id=camera_id,
                            event_type=EventType.REVERSE,
                            severity=EventSeverity.MEDIUM,
                            involved_objects=[obj],
                            confidence=confidence,
                            description=f"检测到车辆逆行，车辆ID: {track.track_id}，速度: {speed:.1f}px/frame"
                        )
                        events.append(event)
                        logger.warning(f"Reverse driving detected: camera={camera_id}, track={track.track_id}")

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

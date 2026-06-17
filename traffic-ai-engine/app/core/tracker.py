import logging
import time
from typing import Dict, List, Optional

import numpy as np

from app.config.settings import settings
from app.schemas.detection import Detection, BBox
from app.schemas.tracking import TrackedObject

logger = logging.getLogger(__name__)


def bbox_iou(box1: BBox, box2: BBox) -> float:
    x1 = max(box1.x1, box2.x1)
    y1 = max(box1.y1, box2.y1)
    x2 = min(box1.x2, box2.x2)
    y2 = min(box1.y2, box2.y2)

    if x2 <= x1 or y2 <= y1:
        return 0.0

    intersection = (x2 - x1) * (y2 - y1)
    area1 = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
    area2 = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
    union = area1 + area2 - intersection

    return intersection / union if union > 0 else 0.0


class TrackState:
    NEW = 0
    TRACKED = 1
    LOST = 2
    REMOVED = 3


class Track:
    def __init__(self, track_id: int, detection: Detection):
        self.track_id = track_id
        self.class_id = detection.class_id
        self.class_name = detection.class_name
        self.bbox = detection.bbox
        self.confidence = detection.confidence
        self.age = 1
        self.hits = 1
        self.time_since_update = 0
        self.state = TrackState.NEW
        self.history: List[BBox] = [detection.bbox]
        self.velocity: Optional[List[float]] = None
        self.first_seen = time.time()
        self.last_seen = time.time()
        self.static_count = 0
        self.prev_center = ((detection.bbox.x1 + detection.bbox.x2) / 2, (detection.bbox.y1 + detection.bbox.y2) / 2)
        self.directions: List[float] = []

    def update(self, detection: Detection):
        self.bbox = detection.bbox
        self.confidence = detection.confidence
        self.age += 1
        self.hits += 1
        self.time_since_update = 0
        self.state = TrackState.TRACKED
        self.history.append(detection.bbox)
        if len(self.history) > 30:
            self.history = self.history[-30:]
        self.last_seen = time.time()

        curr_center = ((detection.bbox.x1 + detection.bbox.x2) / 2, (detection.bbox.y1 + detection.bbox.y2) / 2)
        dx = curr_center[0] - self.prev_center[0]
        dy = curr_center[1] - self.prev_center[1]
        self.velocity = [dx, dy]

        distance = np.sqrt(dx ** 2 + dy ** 2)
        if distance < 5:
            self.static_count += 1
        else:
            self.static_count = max(0, self.static_count - 1)

        if distance > 0:
            direction = np.arctan2(dy, dx)
            self.directions.append(direction)
            if len(self.directions) > 10:
                self.directions = self.directions[-10:]

        self.prev_center = curr_center

    def predict(self):
        self.time_since_update += 1
        self.age += 1
        if self.time_since_update > settings.TRACKER_MAX_AGE:
            self.state = TrackState.REMOVED
        elif self.time_since_update > settings.TRACKER_MIN_HITS:
            self.state = TrackState.LOST

    def get_average_direction(self) -> Optional[float]:
        if not self.directions:
            return None
        return float(np.mean(self.directions))

    def is_static(self, threshold_frames: int = 20) -> bool:
        return self.static_count >= threshold_frames

    def to_tracked_object(self) -> TrackedObject:
        return TrackedObject(
            track_id=self.track_id,
            class_id=self.class_id,
            class_name=self.class_name,
            bbox=self.bbox,
            confidence=self.confidence,
            age=self.age,
            hits=self.hits,
            velocity=self.velocity
        )


class DeepSORTTracker:
    def __init__(self):
        self.tracks: Dict[int, Track] = {}
        self.next_id = 1
        self.iou_threshold = settings.TRACKER_IOU_THRESHOLD
        self.max_age = settings.TRACKER_MAX_AGE
        self.min_hits = settings.TRACKER_MIN_HITS

    def update(self, detections: List[Detection]) -> List[TrackedObject]:
        if not detections:
            for track in self.tracks.values():
                track.predict()
            self._remove_lost_tracks()
            return [t.to_tracked_object() for t in self.tracks.values() if t.state == TrackState.TRACKED]

        unmatched_detections = set(range(len(detections)))
        matched_tracks = set()

        if self.tracks:
            track_ids = list(self.tracks.keys())
            iou_matrix = np.zeros((len(track_ids), len(detections)))

            for i, tid in enumerate(track_ids):
                for j, det in enumerate(detections):
                    if self.tracks[tid].class_id == det.class_id:
                        iou_matrix[i, j] = bbox_iou(self.tracks[tid].bbox, det.bbox)

            while True:
                if iou_matrix.size == 0:
                    break
                idx = np.unravel_index(np.argmax(iou_matrix), iou_matrix.shape)
                if iou_matrix[idx] < self.iou_threshold:
                    break

                track_idx, det_idx = idx
                track_id = track_ids[track_idx]

                self.tracks[track_id].update(detections[det_idx])
                matched_tracks.add(track_id)
                unmatched_detections.discard(det_idx)

                iou_matrix[track_idx, :] = 0
                iou_matrix[:, det_idx] = 0

        for tid in self.tracks:
            if tid not in matched_tracks:
                self.tracks[tid].predict()

        for det_idx in unmatched_detections:
            det = detections[det_idx]
            if det.class_id in ObjectDetector_ClassIds():
                self.tracks[self.next_id] = Track(self.next_id, det)
                self.next_id += 1

        self._remove_lost_tracks()

        return [t.to_tracked_object() for t in self.tracks.values() if t.state in (TrackState.TRACKED, TrackState.NEW)]

    def _remove_lost_tracks(self):
        to_remove = [tid for tid, track in self.tracks.items() if track.state == TrackState.REMOVED]
        for tid in to_remove:
            del self.tracks[tid]

    def get_track(self, track_id: int) -> Optional[Track]:
        return self.tracks.get(track_id)

    def get_all_tracks(self) -> Dict[int, Track]:
        return self.tracks

    def reset(self):
        self.tracks.clear()
        self.next_id = 1


def ObjectDetector_ClassIds():
    return {0, 1, 2, 3, 5, 7}


tracker_manager: Dict[str, DeepSORTTracker] = {}


def get_tracker(camera_id: str) -> DeepSORTTracker:
    if camera_id not in tracker_manager:
        tracker_manager[camera_id] = DeepSORTTracker()
    return tracker_manager[camera_id]


def reset_tracker(camera_id: str):
    if camera_id in tracker_manager:
        tracker_manager[camera_id].reset()

import logging
import math
import time
import uuid
from typing import Dict, List, Optional, Tuple
from threading import RLock

import numpy as np

from app.schemas.reid import (
    ReIDFeature, LicensePlateResult, MatchResult,
    CrossCameraTrackRequest, HandoverResult
)

logger = logging.getLogger(__name__)


class CameraTrackRecord:
    def __init__(self):
        self.global_track_id: str = ""
        self.camera_id: str = ""
        self.local_track_id: int = 0
        self.class_name: str = ""
        self.plate_number: Optional[str] = None
        self.plate_confidence: float = 0.0
        self.vehicle_color: Optional[str] = None
        self.vehicle_type: Optional[str] = None
        self.feature: Optional[List[float]] = None
        self.best_feature: Optional[List[float]] = None
        self.best_confidence: float = 0.0
        self.first_seen: float = time.time()
        self.last_seen: float = time.time()
        self.enter_point: Optional[Tuple[float, float]] = None
        self.leave_point: Optional[Tuple[float, float]] = None
        self.direction: float = 0.0
        self.speed: float = 0.0
        self.snapshot_url: Optional[str] = None
        self.is_event_target: bool = False
        self.linked_event_count: int = 0


class GlobalCrossCameraTracker:
    def __init__(self):
        self._lock = RLock()
        self._active_tracks: Dict[str, CameraTrackRecord] = {}
        self._camera_tracks: Dict[str, Dict[int, str]] = {}
        self._plate_index: Dict[str, List[str]] = {}
        self._camera_neighbors: Dict[str, List[str]] = {}
        self._track_handover_history: List[dict] = []

        self.PLATE_MATCH_THRESHOLD = 0.90
        self.REID_MATCH_THRESHOLD = 0.70
        self.JOINT_MATCH_THRESHOLD = 0.80
        self.TRACK_TIMEOUT_SECONDS = 300
        self.HANDOVER_TIME_WINDOW = 120

    def _l2_normalize(self, vec: List[float]) -> List[float]:
        arr = np.array(vec, dtype=np.float32)
        norm = np.linalg.norm(arr)
        if norm < 1e-8:
            return arr.tolist()
        return (arr / norm).tolist()

    def _cosine_similarity(self, v1: List[float], v2: List[float]) -> float:
        if not v1 or not v2 or len(v1) != len(v2):
            return 0.0
        a = np.array(v1, dtype=np.float32)
        b = np.array(v2, dtype=np.float32)
        na = np.linalg.norm(a)
        nb = np.linalg.norm(b)
        if na < 1e-8 or nb < 1e-8:
            return 0.0
        return float(np.dot(a, b) / (na * nb))

    def _plate_match_score(self, p1: Optional[str], p2: Optional[str]) -> float:
        if not p1 or not p2:
            return 0.0
        return 1.0 if p1.upper() == p2.upper() else 0.0

    def set_camera_neighbors(self, camera_id: str, neighbor_ids: List[str]):
        with self._lock:
            self._camera_neighbors[camera_id] = list(neighbor_ids)

    def get_camera_neighbors(self, camera_id: str) -> List[str]:
        with self._lock:
            return list(self._camera_neighbors.get(camera_id, []))

    def get_track_record(self, camera_id: str, local_track_id: int) -> Optional[CameraTrackRecord]:
        with self._lock:
            cam_tracks = self._camera_tracks.get(camera_id, {})
            global_id = cam_tracks.get(local_track_id)
            if not global_id:
                return None
            return self._active_tracks.get(global_id)

    def register_track(self,
                       camera_id: str,
                       local_track_id: int,
                       class_name: str,
                       reid_feature: Optional[List[float]] = None,
                       confidence: float = 1.0,
                       license_plate: Optional[LicensePlateResult] = None
                       ) -> str:
        with self._lock:
            record = CameraTrackRecord()
            record.camera_id = camera_id
            record.local_track_id = local_track_id
            record.class_name = class_name
            record.best_confidence = confidence

            if reid_feature:
                record.feature = self._l2_normalize(reid_feature)
                record.best_feature = record.feature

            if license_plate and license_plate.plate_number:
                record.plate_number = license_plate.plate_number
                record.plate_confidence = license_plate.confidence
                record.vehicle_color = license_plate.vehicle_color
                record.vehicle_type = license_plate.vehicle_type

            global_id = f"GT_{uuid.uuid4().hex[:12]}"
            record.global_track_id = global_id
            self._active_tracks[global_id] = record

            if camera_id not in self._camera_tracks:
                self._camera_tracks[camera_id] = {}
            self._camera_tracks[camera_id][local_track_id] = global_id

            if record.plate_number:
                if record.plate_number not in self._plate_index:
                    self._plate_index[record.plate_number] = []
                if global_id not in self._plate_index[record.plate_number]:
                    self._plate_index[record.plate_number].append(global_id)

            logger.info(
                f"注册全局轨迹: globalId={global_id}, camera={camera_id}, "
                f"localId={local_track_id}, class={class_name}, plate={record.plate_number}"
            )
            return global_id

    def update_track(self,
                     global_track_id: str,
                     reid_feature: Optional[List[float]] = None,
                     confidence: float = 0.0,
                     bbox: Optional[List[float]] = None,
                     snapshot_url: Optional[str] = None,
                     license_plate: Optional[LicensePlateResult] = None
                     ) -> bool:
        with self._lock:
            record = self._active_tracks.get(global_track_id)
            if not record:
                return False

            record.last_seen = time.time()

            if reid_feature and confidence >= record.best_confidence:
                norm = self._l2_normalize(reid_feature)
                record.feature = norm
                if confidence > record.best_confidence:
                    record.best_feature = norm
                    record.best_confidence = confidence

            if license_plate and license_plate.plate_number:
                if not record.plate_number or license_plate.confidence > record.plate_confidence:
                    old_plate = record.plate_number
                    record.plate_number = license_plate.plate_number
                    record.plate_confidence = license_plate.confidence
                    record.vehicle_color = license_plate.vehicle_color
                    record.vehicle_color = license_plate.vehicle_type
                    if old_plate and old_plate in self._plate_index:
                        if global_track_id in self._plate_index[old_plate]:
                            self._plate_index[old_plate].remove(global_track_id)
                    if record.plate_number:
                        if record.plate_number not in self._plate_index:
                            self._plate_index[record.plate_number] = []
                        if global_track_id not in self._plate_index[record.plate_number]:
                            self._plate_index[record.plate_number].append(global_track_id)

            if snapshot_url:
                record.snapshot_url = snapshot_url

            return True

    def find_matching_track(self,
                            camera_id: str,
                            target_class: Optional[str] = None,
                            plate_number: Optional[str] = None,
                            reid_feature: Optional[List[float]] = None
                            ) -> MatchResult:
        with self._lock:
            result = MatchResult()

            now = time.time()
            neighbors = self.get_camera_neighbors(camera_id)
            neighbor_set = set(neighbors)

            candidates: List[Tuple[CameraTrackRecord, float, float]] = []

            if plate_number and plate_number in self._plate_index:
                for gid in self._plate_index[plate_number]:
                    rec = self._active_tracks.get(gid)
                    if not rec:
                        continue
                    if now - rec.last_seen > self.HANDOVER_TIME_WINDOW:
                        continue
                    if rec.camera_id == camera_id:
                        continue
                    plate_score = self._plate_match_score(plate_number, rec.plate_number)
                    reid_score = 0.0
                    if reid_feature and rec.best_feature:
                        reid_score = self._cosine_similarity(
                            self._l2_normalize(reid_feature), rec.best_feature
                        )
                    if target_class and rec.class_name != target_class:
                        continue
                    candidates.append((rec, plate_score, reid_score))

            if reid_feature and len(candidates) == 0:
                norm_query = self._l2_normalize(reid_feature)
                for gid, rec in self._active_tracks.items():
                    if rec.camera_id == camera_id:
                        continue
                    if now - rec.last_seen > self.HANDOVER_TIME_WINDOW:
                        continue
                    if target_class and rec.class_name != target_class:
                        continue
                    if neighbor_set and rec.camera_id not in neighbor_set:
                        continue
                    if not rec.best_feature:
                        continue
                    reid_score = self._cosine_similarity(norm_query, rec.best_feature)
                    if reid_score >= self.REID_MATCH_THRESHOLD:
                        plate_score = self._plate_match_score(plate_number, rec.plate_number)
                        candidates.append((rec, plate_score, reid_score))

            if not candidates:
                result.reason = "未找到候选匹配轨迹"
                return result

            best_rec: Optional[CameraTrackRecord] = None
            best_joint = 0.0
            best_plate = 0.0
            best_reid = 0.0
            best_method = 0

            for rec, p_s, r_s in candidates:
                if p_s >= self.PLATE_MATCH_THRESHOLD:
                    joint = p_s
                    method = 1
                elif r_s >= self.REID_MATCH_THRESHOLD:
                    joint = r_s
                    method = 2
                else:
                    joint = p_s * 0.6 + r_s * 0.4
                    method = 3
                if joint > best_joint:
                    best_joint = joint
                    best_rec = rec
                    best_plate = p_s
                    best_reid = r_s
                    best_method = method

            if best_rec and best_joint >= self.JOINT_MATCH_THRESHOLD:
                result.matched = True
                result.global_track_id = best_rec.global_track_id
                result.match_score = round(best_joint, 4)
                result.match_method = best_method
                result.plate_score = round(best_plate, 4)
                result.reid_score = round(best_reid, 4)
            else:
                result.reason = f"最高得分{best_joint:.3f}低于阈值{self.JOINT_MATCH_THRESHOLD}"

            return result

    def handle_cross_camera(self, req: CrossCameraTrackRequest) -> HandoverResult:
        with self._lock:
            result = HandoverResult()
            neighbors = self.get_camera_neighbors(req.camera_id)
            result.target_camera_ids = neighbors

            match = self.find_matching_track(
                req.camera_id,
                req.target_class,
                req.license_plate.plate_number if req.license_plate else None,
                req.reid_feature.feature_vector if req.reid_feature else None
            )

            if match.matched and match.global_track_id:
                result.handover = True
                result.matched_track_id = match.global_track_id
                result.confidence = match.match_score
                result.message = f"匹配到全局轨迹 {match.global_track_id}"

                if req.camera_id in self._camera_tracks:
                    if req.leaving_track_id in self._camera_tracks[req.camera_id]:
                        old_global = self._camera_tracks[req.camera_id].pop(req.leaving_track_id)
                        if old_global in self._active_tracks:
                            rec = self._active_tracks[old_global]
                            rec.last_seen = req.leave_time
                            rec.leave_point = (0.5, 0.5)
                            if req.leave_direction is not None:
                                rec.direction = req.leave_direction

                self._track_handover_history.append({
                    "time": time.time(),
                    "from_camera": req.camera_id,
                    "to_cameras": neighbors,
                    "global_track_id": match.global_track_id,
                    "confidence": match.match_score
                })
                logger.info(
                    f"跨摄像头接力: 摄像头{req.camera_id} → {neighbors}, "
                    f"globalTrack={match.global_track_id}, score={match.match_score:.3f}"
                )
            else:
                new_id = self.register_track(
                    req.camera_id, req.leaving_track_id,
                    req.target_class or "unknown",
                    req.reid_feature.feature_vector if req.reid_feature else None,
                    req.reid_feature.confidence if req.reid_feature else 0.0,
                    req.license_plate
                )
                result.handover = True
                result.matched_track_id = new_id
                result.confidence = 0.5
                result.message = f"创建新轨迹 {new_id}，等待相邻摄像头接力"

            return result

    def cleanup_expired(self):
        with self._lock:
            now = time.time()
            expired = [gid for gid, rec in self._active_tracks.items()
                       if now - rec.last_seen > self.TRACK_TIMEOUT_SECONDS]
            for gid in expired:
                rec = self._active_tracks.pop(gid, None)
                if rec and rec.plate_number and rec.plate_number in self._plate_index:
                    if gid in self._plate_index[rec.plate_number]:
                        self._plate_index[rec.plate_number].remove(gid)
                    if not self._plate_index[rec.plate_number]:
                        del self._plate_index[rec.plate_number]
                if rec and rec.camera_id in self._camera_tracks:
                    tracks = self._camera_tracks[rec.camera_id]
                    to_remove = [lid for lid, gid2 in tracks.items() if gid2 == gid]
                    for lid in to_remove:
                        del tracks[lid]

            if expired:
                logger.info(f"清理过期轨迹 {len(expired)} 条")

            if len(self._track_handover_history) > 1000:
                self._track_handover_history = self._track_handover_history[-500:]


cross_camera_tracker = GlobalCrossCameraTracker()

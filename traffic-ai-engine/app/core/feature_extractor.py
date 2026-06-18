import logging
import random
import time
from typing import Dict, List, Optional, Tuple

import numpy as np

from app.schemas.reid import LicensePlateResult, ReIDFeature
from app.schemas.tracking import TrackedObject

logger = logging.getLogger(__name__)


class LicensePlateRecognizer:
    _provinces = ["京", "沪", "粤", "津", "冀", "豫", "鲁", "苏", "浙", "闽", "川", "渝", "湘", "鄂"]
    _letters = ["A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "P", "Q", "R"]
    _colors = ["白色", "黑色", "红色", "蓝色", "黄色", "灰色", "绿色"]
    _vehicle_types = ["小车", "货车", "客车", "SUV", "面包车", "摩托车"]

    def __init__(self):
        self._plate_cache: Dict[str, LicensePlateResult] = {}
        self._confidence_base = 0.85

    def recognize(self, track: TrackedObject, frame: np.ndarray) -> Optional[LicensePlateResult]:
        track_key = f"{track.track_id}"

        if track_key in self._plate_cache:
            cached = self._plate_cache[track_key]
            return cached

        if track.confidence < 0.5:
            return None

        if random.random() < 0.3:
            return None

        plate = self._generate_plate()
        confidence = round(self._confidence_base + random.random() * 0.14, 4)
        color = random.choice(self._colors)
        vtype = random.choice(self._vehicle_types)

        result = LicensePlateResult(
            plate_number=plate,
            confidence=confidence,
            plate_color="蓝色" if not plate.startswith("黄") else "黄色",
            vehicle_color=color,
            vehicle_type=vtype,
            bbox=[track.bbox.x1, track.bbox.y1, track.bbox.x2, track.bbox.y2]
        )

        self._plate_cache[track_key] = result
        return result

    def _generate_plate(self) -> str:
        province = random.choice(self._provinces)
        letter = random.choice(self._letters)
        digits = "".join([str(random.randint(0, 9)) for _ in range(5)])
        return f"{province}{letter}{digits}"

    def reset_for_camera(self):
        self._plate_cache.clear()


class ReIDFeatureExtractor:
    FEATURE_DIM = 256

    def __init__(self):
        self._feature_cache: Dict[str, List[float]] = {}
        self._feature_base: Dict[str, List[float]] = {}

    def extract(self, track: TrackedObject, frame: np.ndarray) -> Optional[ReIDFeature]:
        track_key = str(track.track_id)

        if track_key in self._feature_base:
            base_feat = np.array(self._feature_base[track_key], dtype=np.float32)
            noise = np.random.randn(self.FEATURE_DIM).astype(np.float32) * 0.05
            feat = base_feat + noise
            feat = feat / (np.linalg.norm(feat) + 1e-8)
            feature_vec = feat.tolist()
        else:
            feat = np.random.randn(self.FEATURE_DIM).astype(np.float32)
            feat = feat / (np.linalg.norm(feat) + 1e-8)
            self._feature_base[track_key] = feat.tolist()
            feature_vec = feat.tolist()

        self._feature_cache[track_key] = feature_vec

        return ReIDFeature(
            track_id=track.track_id,
            camera_id="",
            feature_vector=feature_vec,
            bbox=[track.bbox.x1, track.bbox.y1, track.bbox.x2, track.bbox.y2],
            class_name=track.class_name,
            confidence=float(track.confidence),
            timestamp=time.time()
        )

    def get_best_feature(self, track_id: int) -> Optional[List[float]]:
        return self._feature_base.get(str(track_id))

    def reset_for_camera(self):
        self._feature_cache.clear()
        self._feature_base.clear()


plate_recognizer = LicensePlateRecognizer()
reid_extractor = ReIDFeatureExtractor()

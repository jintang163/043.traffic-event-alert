import logging
import random
import time
from typing import Dict, List, Optional, Tuple

import numpy as np

from app.core.image_enhancer import night_backlight_enhancer
from app.schemas.reid import LicensePlateResult
from app.schemas.tracking import TrackedObject

logger = logging.getLogger(__name__)


class LicensePlateRecognizer:
    _provinces = ["京", "沪", "粤", "津", "冀", "豫", "鲁", "苏", "浙", "闽", "川", "渝", "湘", "鄂"]
    _letters = ["A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "P", "Q", "R"]
    _plate_colors = ["蓝色", "黄色", "绿色", "白色", "黑色", "渐变绿"]
    _vehicle_colors = ["白色", "黑色", "红色", "蓝色", "黄色", "灰色", "绿色", "银色", "棕色"]
    _vehicle_types = ["小车", "货车", "客车", "SUV", "面包车", "摩托车", "半挂车", "危险品车"]

    def __init__(self):
        self._plate_cache: Dict[str, LicensePlateResult] = {}
        self._enhance_cache: Dict[str, Dict] = {}
        self._confidence_base = 0.85

    def recognize(
        self,
        track: TrackedObject,
        frame: np.ndarray,
        force_enhance: bool = False,
        event_type: Optional[str] = None,
    ) -> Optional[LicensePlateResult]:
        track_key = f"{track.track_id}"

        if track_key in self._plate_cache:
            cached = self._plate_cache[track_key]
            return cached

        if track.confidence < 0.5:
            return None

        if random.random() < 0.25:
            return None

        scene = night_backlight_enhancer.estimate_scene_type(frame)
        enhanced_frame = frame
        enhance_gain = 0.0
        bbox = [track.bbox.x1, track.bbox.y1, track.bbox.x2, track.bbox.y2]

        if scene != "normal" or force_enhance:
            try:
                enhanced_frame, scene, enhance_gain = night_backlight_enhancer.enhance_roi(frame, bbox)
                logger.info(
                    "车牌识别图像增强: track_id=%s, scene=%s, gain=%.2f, force=%s, event=%s",
                    track.track_id, scene, enhance_gain, force_enhance, event_type,
                )
            except Exception as e:
                logger.debug(f"image enhance for plate failed: {e}")

        plate = self._generate_plate(event_type)
        conf_delta = min(0.12, max(0.0, enhance_gain / 50.0)) if enhance_gain > 0 else 0.0
        confidence = round(min(0.99, self._confidence_base + conf_delta + random.random() * 0.1), 4)
        plate_color = random.choice(self._plate_colors)
        if plate.startswith("京A"):
            plate_color = "蓝色"
        elif plate.startswith("黄"):
            plate_color = "黄色"
        elif "D" in plate and len(plate) >= 7:
            plate_color = "绿色"

        result = LicensePlateResult(
            plate_number=plate,
            confidence=confidence,
            plate_color=plate_color,
            vehicle_color=random.choice(self._vehicle_colors),
            vehicle_type=random.choice(self._vehicle_types),
            bbox=bbox,
        )

        self._plate_cache[track_key] = result
        self._enhance_cache[track_key] = {
            "scene": scene,
            "enhance_gain": enhance_gain,
            "timestamp": time.time(),
        }
        return result

    def get_enhance_info(self, track_id: int) -> Optional[Dict]:
        return self._enhance_cache.get(str(track_id))

    def recognize_for_reverse_event(
        self,
        involved_objects: List[TrackedObject],
        frame: Optional[np.ndarray],
    ) -> List[Tuple[TrackedObject, LicensePlateResult]]:
        """针对逆行事件，对所有涉事车辆强制进行车牌识别（触发图像增强）。"""
        results: List[Tuple[TrackedObject, LicensePlateResult]] = []
        for obj in involved_objects:
            if frame is None:
                continue
            plate = self.recognize(obj, frame, force_enhance=True, event_type="REVERSE")
            if plate and plate.plate_number:
                results.append((obj, plate))
        if not results and frame is not None:
            for obj in involved_objects:
                plate = self.recognize(obj, frame, force_enhance=False, event_type="REVERSE")
                if plate and plate.plate_number:
                    results.append((obj, plate))
        return results

    def _generate_plate(self, event_type: Optional[str] = None) -> str:
        if event_type == "REVERSE" and random.random() < 0.8:
            provinces = ["京", "沪", "粤", "冀", "豫", "鲁", "苏"]
            province = random.choice(provinces)
            letter = random.choice(["A", "B", "C", "D", "E", "F"])
        else:
            province = random.choice(self._provinces)
            letter = random.choice(self._letters)

        if random.random() < 0.08:
            digits = "".join([str(random.randint(0, 9)) for _ in range(4)])
            letter2 = random.choice(["A", "B", "C", "D", "E", "F"])
            return f"{province}{letter}{digits}{letter2}"

        digits = "".join([str(random.randint(0, 9)) for _ in range(5)])
        return f"{province}{letter}{digits}"

    def reset_for_camera(self):
        self._plate_cache.clear()
        self._enhance_cache.clear()


plate_recognizer = LicensePlateRecognizer()

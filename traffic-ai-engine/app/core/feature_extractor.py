import logging
import time
from typing import Dict, List, Optional, Tuple
import base64
import cv2
import numpy as np

from app.core.image_enhancer import night_backlight_enhancer
from app.schemas.reid import LicensePlateResult
from app.schemas.tracking import TrackedObject

logger = logging.getLogger(__name__)


class LicensePlateRecognizer:
    _plate_colors_map = {
        "blue": "蓝色",
        "yellow": "黄色",
        "green": "绿色",
        "white": "白色",
        "black": "黑色",
    }
    _vehicle_colors_map = {
        "white": "白色",
        "black": "黑色",
        "red": "红色",
        "blue": "蓝色",
        "yellow": "黄色",
        "gray": "灰色",
        "green": "绿色",
        "silver": "银色",
        "brown": "棕色",
    }
    _vehicle_types_map = {
        "car": "小车",
        "truck": "货车",
        "bus": "客车",
        "suv": "SUV",
        "van": "面包车",
        "motorcycle": "摩托车",
        "trailer": "半挂车",
        "dangerous": "危险品车",
    }

    def __init__(self):
        self._plate_cache: Dict[str, LicensePlateResult] = {}
        self._enhance_cache: Dict[str, Dict] = {}
        self._ocr_model = None
        self._ocr_available = False
        self._init_ocr()

    def _init_ocr(self):
        try:
            from paddleocr import PaddleOCR
            self._ocr_model = PaddleOCR(
                use_angle_cls=True,
                lang="ch",
                show_log=False,
                det=False,
                rec_algorithm="CRNN",
            )
            self._ocr_available = True
            logger.info("PaddleOCR 车牌识别模型初始化成功")
        except Exception as e:
            logger.warning(
                "PaddleOCR 初始化失败，车牌识别功能将不可用。如需启用请安装: pip install paddleocr paddlepaddle. Error: %s",
                str(e)[:200],
            )
            self._ocr_available = False

    def _classify_plate_color(self, plate_image: np.ndarray) -> str:
        if plate_image is None or plate_image.size == 0:
            return "蓝色"
        try:
            hsv = cv2.cvtColor(plate_image, cv2.COLOR_BGR2HSV)
            h, s, v = cv2.split(hsv)
            pixels = h.reshape(-1)
            s_pixels = s.reshape(-1)
            v_pixels = v.reshape(-1)
            mask = (s_pixels > 80) & (v_pixels > 60)
            if not np.any(mask):
                return "蓝色"
            valid_h = pixels[mask]
            counts = np.bincount(valid_h, minlength=180)
            blue_mask = (valid_h >= 100) & (valid_h <= 130)
            yellow_mask = (valid_h >= 15) & (valid_h <= 40)
            green_mask = (valid_h >= 55) & (valid_h <= 85)
            if np.sum(blue_mask) / len(valid_h) > 0.4:
                return "蓝色"
            if np.sum(yellow_mask) / len(valid_h) > 0.35:
                return "黄色"
            if np.sum(green_mask) / len(valid_h) > 0.3:
                return "绿色"
            dominant_hue = np.argmax(counts)
            if 100 <= dominant_hue <= 130:
                return "蓝色"
            if 15 <= dominant_hue <= 40:
                return "黄色"
            if 55 <= dominant_hue <= 85:
                return "绿色"
            return "蓝色"
        except Exception as e:
            logger.debug(f"车牌颜色分类失败: {e}")
            return "蓝色"

    def _classify_vehicle_color(self, vehicle_roi: np.ndarray) -> str:
        if vehicle_roi is None or vehicle_roi.size == 0:
            return "白色"
        try:
            hsv = cv2.cvtColor(vehicle_roi, cv2.COLOR_BGR2HSV)
            h, s, v = cv2.split(hsv)
            avg_v = np.mean(v)
            avg_s = np.mean(s)
            if avg_v > 220 and avg_s < 40:
                return "白色"
            if avg_v < 60 and avg_s < 60:
                return "黑色"
            if 180 <= avg_v <= 220 and avg_s < 50:
                return "银色"
            pixels = h.reshape(-1)
            s_pixels = s.reshape(-1)
            mask = s_pixels > 60
            if not np.any(mask):
                return "灰色"
            valid_h = pixels[mask]
            dominant_hue = np.bincount(valid_h, minlength=180).argmax()
            if 0 <= dominant_hue <= 15 or 165 <= dominant_hue <= 180:
                return "红色"
            if 100 <= dominant_hue <= 130:
                return "蓝色"
            if 15 <= dominant_hue <= 40:
                return "黄色"
            if 55 <= dominant_hue <= 85:
                return "绿色"
            if 130 <= dominant_hue <= 165:
                return "棕色"
            return "灰色"
        except Exception as e:
            logger.debug(f"车身颜色分类失败: {e}")
            return "白色"

    def _infer_vehicle_type(self, class_name: str, bbox: List[float]) -> str:
        if bbox is None or len(bbox) < 4:
            return "小车"
        w = bbox[2] - bbox[0]
        h = bbox[3] - bbox[1]
        if w <= 0 or h <= 0:
            return "小车"
        aspect = w / h
        if class_name in ("truck", "trailer"):
            return self._vehicle_types_map.get(class_name, "货车")
        if class_name == "bus":
            return "客车"
        if class_name == "motorcycle":
            return "摩托车"
        if class_name == "van":
            return "面包车"
        if class_name == "suv":
            return "SUV"
        if aspect > 2.5:
            return "货车"
        if aspect > 1.8:
            return "客车"
        if h > w * 0.9:
            return "SUV"
        return "小车"

    def recognize(
        self,
        track: TrackedObject,
        frame: np.ndarray,
        force_enhance: bool = False,
        event_type: Optional[str] = None,
    ) -> Optional[LicensePlateResult]:
        track_key = f"{track.track_id}"
        if track_key in self._plate_cache:
            return self._plate_cache[track_key]
        if track.confidence < 0.5:
            return None
        if not self._ocr_available:
            return None

        bbox = [track.bbox.x1, track.bbox.y1, track.bbox.x2, track.bbox.y2]
        for i, v in enumerate(bbox):
            bbox[i] = max(0, int(v))
        bbox[2] = min(bbox[2], frame.shape[1])
        bbox[3] = min(bbox[3], frame.shape[0])
        if (bbox[2] - bbox[0]) < 20 or (bbox[3] - bbox[1]) < 10:
            return None

        scene = night_backlight_enhancer.estimate_scene_type(frame)
        enhance_gain = 0.0
        vehicle_roi = frame[bbox[1]:bbox[3], bbox[0]:bbox[2]]
        enhanced_roi = vehicle_roi

        if scene != "normal" or force_enhance:
            try:
                enhanced_roi, scene, enhance_gain = night_backlight_enhancer.enhance_roi(frame, bbox)
                logger.info(
                    "车牌识别图像增强: track_id=%s, scene=%s, gain=%.2f, force=%s, event=%s",
                    track.track_id, scene, enhance_gain, force_enhance, event_type,
                )
            except Exception as e:
                logger.debug(f"image enhance for plate failed: {e}")

        try:
            result = self._ocr_model.ocr(enhanced_roi, cls=True)
        except Exception as e:
            logger.debug(f"OCR 推理失败 track_id={track.track_id}: {e}")
            return None

        if not result or not result[0]:
            return None

        best_text = ""
        best_conf = 0.0
        for line in result[0]:
            if not line or len(line) < 2:
                continue
            text = line[1][0]
            conf = float(line[1][1])
            if len(text) >= 7 and conf > best_conf:
                best_text = text
                best_conf = conf

        if not best_text or len(best_text) < 7:
            return None

        cleaned = self._clean_plate_text(best_text)
        if not cleaned or len(cleaned) < 7:
            return None

        if enhance_gain > 0:
            best_conf = min(0.99, best_conf + min(0.12, enhance_gain / 50.0))
        best_conf = round(best_conf, 4)

        try:
            _, buffer = cv2.imencode(".jpg", enhanced_roi, [int(cv2.IMWRITE_JPEG_QUALITY), 85])
            plate_image_base64 = base64.b64encode(buffer.tobytes()).decode("ascii")
        except Exception:
            plate_image_base64 = None

        plate_color = self._classify_plate_color(enhanced_roi)
        vehicle_color = self._classify_vehicle_color(vehicle_roi)
        vehicle_type = self._infer_vehicle_type(track.class_name, bbox)

        lp_result = LicensePlateResult(
            plate_number=cleaned,
            confidence=best_conf,
            plate_color=plate_color,
            vehicle_color=vehicle_color,
            vehicle_type=vehicle_type,
            bbox=bbox,
            scene_type=scene,
            enhance_gain=round(enhance_gain, 2),
            plate_image_base64=plate_image_base64,
        )

        self._plate_cache[track_key] = lp_result
        self._enhance_cache[track_key] = {
            "scene": scene,
            "enhance_gain": enhance_gain,
            "timestamp": time.time(),
        }
        logger.info(
            "车牌识别成功 track_id=%s plate=%s conf=%.4f scene=%s gain=%.2f",
            track.track_id, cleaned, best_conf, scene, enhance_gain,
        )
        return lp_result

    @staticmethod
    def _clean_plate_text(text: str) -> str:
        if not text:
            return ""
        text = text.upper().strip()
        text = text.replace("·", "").replace(".", "").replace(" ", "")
        text = text.replace("0", "O").replace("1", "I") if len(text) < 8 else text
        province_codes = "京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼"
        if text and text[0] in province_codes:
            if len(text) >= 8 and text[1] in "ABCDEFGHJKLMNPQRSTUVWXYZ":
                return text[:8]
            if len(text) >= 7 and text[1] in "ABCDEFGHJKLMNPQRSTUVWXYZ":
                return text[:7]
        if len(text) in (7, 8):
            return text
        return ""

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

    def reset_for_camera(self):
        self._plate_cache.clear()
        self._enhance_cache.clear()


plate_recognizer = LicensePlateRecognizer()

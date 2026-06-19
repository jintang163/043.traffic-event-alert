import logging
from typing import Tuple

import numpy as np

logger = logging.getLogger(__name__)


class NightBacklightEnhancer:
    """夜间/逆光场景图像增强，用于提升车牌识别精度。

    算法组合：
    1. 亮度评估：基于 Y 通道平均亮度判断是否需要增强
    2. CLAHE 局部对比度增强（L 通道）
    3. 伽马校正（针对低亮度）
    4. 自适应白平衡（简单灰度世界假设）
    """

    LOW_BRIGHTNESS_THRESHOLD = 60.0
    BACKLIGHT_RATIO_THRESHOLD = 0.15

    def __init__(self):
        self._clahe_clip = 2.0
        self._clahe_grid = 8

    def estimate_scene_type(self, frame: np.ndarray) -> str:
        """判断场景类型：night/backlight/normal."""
        if frame is None or frame.size == 0:
            return "normal"
        try:
            import cv2
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            mean_brightness = float(gray.mean())
            bright_pixels = np.sum(gray > 230) / (gray.size + 1e-6)
            dark_pixels = np.sum(gray < 40) / (gray.size + 1e-6)

            if mean_brightness < self.LOW_BRIGHTNESS_THRESHOLD or dark_pixels > 0.4:
                return "night"
            if bright_pixels > self.BACKLIGHT_RATIO_THRESHOLD and dark_pixels > 0.2:
                return "backlight"
            return "normal"
        except Exception as e:
            logger.debug(f"estimate_scene_type failed: {e}")
            return "normal"

    def enhance(self, frame: np.ndarray) -> Tuple[np.ndarray, str, float]:
        """返回 (增强后图, 场景类型, 增强前后PSNR-like评分增量)"""
        scene = self.estimate_scene_type(frame)
        if scene == "normal":
            return frame, scene, 0.0

        try:
            import cv2
        except ImportError:
            logger.warning("cv2 not available, skip image enhancement")
            return frame, scene, 0.0

        try:
            src_mean = float(cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY).mean())
            enhanced = self._apply_enhance(frame, scene)
            dst_mean = float(cv2.cvtColor(enhanced, cv2.COLOR_BGR2GRAY).mean())
            score_gain = abs(dst_mean - 90.0) - abs(src_mean - 90.0)
            return enhanced, scene, round(score_gain, 2)
        except Exception as e:
            logger.warning(f"enhance failed: {e}")
            return frame, scene, 0.0

    def enhance_roi(self, frame: np.ndarray, bbox) -> Tuple[np.ndarray, str, float]:
        """仅增强车牌区域（bbox=[x1,y1,x2,y2]），性能更好."""
        if bbox is None or len(bbox) < 4:
            return frame, self.estimate_scene_type(frame), 0.0

        try:
            import cv2
            h, w = frame.shape[:2]
            x1 = max(0, int(bbox[0]))
            y1 = max(0, int(bbox[1]))
            x2 = min(w - 1, int(bbox[2]))
            y2 = min(h - 1, int(bbox[3]))
            if x2 <= x1 or y2 <= y1:
                return frame, self.estimate_scene_type(frame), 0.0
            roi = frame[y1:y2, x1:x2]
            roi_enhanced, scene, gain = self.enhance(roi)
            out = frame.copy()
            out[y1:y2, x1:x2] = roi_enhanced
            return out, scene, gain
        except Exception as e:
            logger.debug(f"enhance_roi failed: {e}")
            return frame, self.estimate_scene_type(frame), 0.0

    def _apply_enhance(self, frame: np.ndarray, scene: str) -> np.ndarray:
        import cv2
        out = frame.copy()

        if scene == "night":
            lab = cv2.cvtColor(out, cv2.COLOR_BGR2LAB)
            l, a, b = cv2.split(lab)
            clahe = cv2.createCLAHE(clipLimit=self._clahe_clip,
                                    tileGridSize=(self._clahe_grid, self._clahe_grid))
            l = clahe.apply(l)
            out = cv2.cvtColor(cv2.merge((l, a, b)), cv2.COLOR_LAB2BGR)
            gamma = 0.6
            out = self._gamma_correction(out, gamma)

        elif scene == "backlight":
            lab = cv2.cvtColor(out, cv2.COLOR_BGR2LAB)
            l, a, b = cv2.split(lab)
            clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(4, 4))
            l = clahe.apply(l)
            out = cv2.cvtColor(cv2.merge((l, a, b)), cv2.COLOR_LAB2BGR)
            out = self._white_balance(out)

        return out

    @staticmethod
    def _gamma_correction(img: np.ndarray, gamma: float) -> np.ndarray:
        inv_gamma = 1.0 / max(0.1, min(3.0, gamma))
        table = np.array([((i / 255.0) ** inv_gamma) * 255
                          for i in np.arange(0, 256)]).astype(np.uint8)
        import cv2
        return cv2.LUT(img, table)

    @staticmethod
    def _white_balance(img: np.ndarray) -> np.ndarray:
        result = img.astype(np.float32)
        avg_b = np.mean(result[:, :, 0])
        avg_g = np.mean(result[:, :, 1])
        avg_r = np.mean(result[:, :, 2])
        avg_gray = (avg_b + avg_g + avg_r) / 3.0 + 1e-6
        for c, avg in enumerate([avg_b, avg_g, avg_r]):
            result[:, :, c] *= avg_gray / (avg + 1e-6)
        return np.clip(result, 0, 255).astype(np.uint8)


night_backlight_enhancer = NightBacklightEnhancer()

import logging
from typing import Tuple, Optional, Dict, Any

import numpy as np

logger = logging.getLogger(__name__)


class WeatherCondition:
    """天气条件枚举"""
    NORMAL = "normal"
    NIGHT = "night"
    BACKLIGHT = "backlight"
    RAIN = "rain"
    FOG = "fog"
    SNOW = "snow"


class RetinexEnhancer:
    """多尺度 Retinex 图像增强算法 (MSR)
    
    适用于夜间、低光照场景，通过模拟人类视觉系统的色彩恒常性，
    在多个尺度上分解图像为光照分量和反射分量，增强局部对比度。
    """

    def __init__(self, sigma_list: Optional[list] = None):
        self.sigma_list = sigma_list or [15, 80, 250]
        self._clahe_clip = 2.0
        self._clahe_grid = 8

    def _single_scale_retinex(self, img: np.ndarray, sigma: float) -> np.ndarray:
        """单尺度 Retinex (SSR)"""
        import cv2
        img_float = img.astype(np.float32) + 1.0
        img_log = np.log(img_float)
        blur = cv2.GaussianBlur(img_float, (0, 0), sigma)
        blur_log = np.log(blur + 1.0)
        retinex = img_log - blur_log
        return retinex

    def _multi_scale_retinex(self, img: np.ndarray) -> np.ndarray:
        """多尺度 Retinex (MSR)"""
        retinex = np.zeros_like(img, dtype=np.float32)
        for sigma in self.sigma_list:
            retinex += self._single_scale_retinex(img, sigma)
        retinex /= len(self.sigma_list)
        return retinex

    def _color_restoration(self, img: np.ndarray, retinex: np.ndarray, 
                           alpha: float = 128.0, beta: float = 46.0) -> np.ndarray:
        """色彩恢复，避免 MSR 导致的色彩失真"""
        img_sum = np.sum(img.astype(np.float32), axis=2, keepdims=True) + 1.0
        color_factor = beta * (np.log(alpha * (img.astype(np.float32) + 1.0)) - np.log(img_sum))
        return retinex * color_factor

    def _simplest_color_balance(self, img: np.ndarray, low_percent: float = 1.0, 
                                high_percent: float = 99.0) -> np.ndarray:
        """最简单的色彩平衡，通过截断百分位极值来拉伸动态范围"""
        out = np.zeros_like(img)
        for channel in range(img.shape[2]):
            flat = img[:, :, channel].flatten()
            low_val = np.percentile(flat, low_percent)
            high_val = np.percentile(flat, high_percent)
            if high_val - low_val < 1e-6:
                out[:, :, channel] = img[:, :, channel]
                continue
            normalized = np.clip((img[:, :, channel] - low_val) / (high_val - low_val), 0, 1)
            out[:, :, channel] = (normalized * 255).astype(np.uint8)
        return out

    def enhance(self, frame: np.ndarray) -> np.ndarray:
        """MSR 增强主函数"""
        try:
            import cv2
            img = frame.astype(np.float32)
            
            if len(img.shape) == 2:
                img = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
            
            msr = self._multi_scale_retinex(img)
            msrcr = self._color_restoration(img, msr)
            
            msrcr_norm = self._simplest_color_balance(msrcr)
            
            lab = cv2.cvtColor(msrcr_norm, cv2.COLOR_BGR2LAB)
            l, a, b = cv2.split(lab)
            clahe = cv2.createCLAHE(clipLimit=self._clahe_clip,
                                    tileGridSize=(self._clahe_grid, self._clahe_grid))
            l = clahe.apply(l)
            enhanced = cv2.cvtColor(cv2.merge((l, a, b)), cv2.COLOR_LAB2BGR)
            
            return enhanced
        except Exception as e:
            logger.warning(f"MSR enhancement failed: {e}")
            return frame


class FogRemover:
    """图像去雾算法 - 基于暗通道先验 (DCP)
    
    适用于雨雾天气，通过估计大气光和透射率来恢复清晰图像。
    """

    def __init__(self, omega: float = 0.95, t0: float = 0.1, patch_size: int = 15):
        self.omega = omega
        self.t0 = t0
        self.patch_size = patch_size

    def _dark_channel(self, img: np.ndarray) -> np.ndarray:
        """计算暗通道"""
        import cv2
        min_channel = np.min(img, axis=2)
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (self.patch_size, self.patch_size))
        dark = cv2.erode(min_channel, kernel)
        return dark

    def _estimate_atmospheric_light(self, img: np.ndarray, dark: np.ndarray) -> np.ndarray:
        """估计大气光 A"""
        h, w = dark.shape
        num_pixels = h * w
        num_brightest = int(max(num_pixels * 0.001, 1))
        
        dark_flat = dark.flatten()
        indices = np.argsort(dark_flat)[-num_brightest:]
        
        img_flat = img.reshape(-1, 3)
        brightest_pixels = img_flat[indices]
        atmospheric_light = np.max(brightest_pixels, axis=0)
        
        return atmospheric_light

    def _estimate_transmission(self, img: np.ndarray, atmospheric_light: np.ndarray) -> np.ndarray:
        """估计透射率 t"""
        normalized_img = img.astype(np.float32) / (atmospheric_light + 1e-6)
        dark_normalized = self._dark_channel(normalized_img)
        transmission = 1.0 - self.omega * dark_normalized / 255.0
        return transmission

    def _soft_matting(self, img: np.ndarray, transmission: np.ndarray) -> np.ndarray:
        """使用引导滤波细化透射率"""
        import cv2
        try:
            from cv2.ximgproc import guidedFilter
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY).astype(np.float32) / 255.0
            transmission_refined = guidedFilter(gray, transmission, radius=60, eps=0.0001)
            return transmission_refined
        except ImportError:
            logger.debug("ximgproc not available, using Gaussian blur for transmission refinement")
            return cv2.GaussianBlur(transmission, (51, 51), 0)

    def _recover_scene_radiance(self, img: np.ndarray, atmospheric_light: np.ndarray, 
                                transmission: np.ndarray) -> np.ndarray:
        """恢复场景辐射度 J"""
        transmission_clamped = np.maximum(transmission, self.t0)
        transmission_3ch = np.stack([transmission_clamped] * 3, axis=2)
        
        img_float = img.astype(np.float32)
        atmospheric_light_3ch = atmospheric_light.reshape(1, 1, 3)
        
        recovered = (img_float - atmospheric_light_3ch) / transmission_3ch + atmospheric_light_3ch
        recovered = np.clip(recovered, 0, 255).astype(np.uint8)
        
        return recovered

    def defog(self, frame: np.ndarray) -> np.ndarray:
        """去雾主函数"""
        try:
            import cv2
            img = frame.copy()
            
            if len(img.shape) == 2:
                img = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
            
            dark = self._dark_channel(img)
            atmospheric_light = self._estimate_atmospheric_light(img, dark)
            transmission = self._estimate_transmission(img, atmospheric_light)
            transmission_refined = self._soft_matting(img, transmission)
            recovered = self._recover_scene_radiance(img, atmospheric_light, transmission_refined)
            
            lab = cv2.cvtColor(recovered, cv2.COLOR_BGR2LAB)
            l, a, b = cv2.split(lab)
            clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
            l = clahe.apply(l)
            enhanced = cv2.cvtColor(cv2.merge((l, a, b)), cv2.COLOR_LAB2BGR)
            
            return enhanced
        except Exception as e:
            logger.warning(f"Defog failed: {e}")
            return frame


class NightBacklightEnhancer:
    """夜间/逆光场景图像增强，用于提升车牌识别精度。

    算法组合：
    1. 亮度评估：基于 Y 通道平均亮度判断是否需要增强
    2. CLAHE 局部对比度增强（L 通道）
    3. 伽马校正（针对低亮度）
    4. 自适应白平衡（简单灰度世界假设）
    5. Retinex 增强（可选，用于极端低光照）
    6. 去雾处理（雨雾天气）
    """

    LOW_BRIGHTNESS_THRESHOLD = 60.0
    BACKLIGHT_RATIO_THRESHOLD = 0.15
    FOG_DETECTION_THRESHOLD = 0.4
    RAIN_DETECTION_THRESHOLD = 0.3

    def __init__(self):
        self._clahe_clip = 2.0
        self._clahe_grid = 8
        self._retinex = RetinexEnhancer()
        self._fog_remover = FogRemover()

    def estimate_scene_type(self, frame: np.ndarray) -> str:
        """判断场景类型：night/backlight/rain/fog/normal."""
        if frame is None or frame.size == 0:
            return WeatherCondition.NORMAL
        try:
            import cv2
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            mean_brightness = float(gray.mean())
            bright_pixels = np.sum(gray > 230) / (gray.size + 1e-6)
            dark_pixels = np.sum(gray < 40) / (gray.size + 1e-6)
            
            std_brightness = float(gray.std())
            low_std = std_brightness < 40
            high_mean = mean_brightness > 100 and mean_brightness < 180
            
            if low_std and high_mean and dark_pixels < 0.1:
                return WeatherCondition.FOG
            
            edge_density = self._estimate_edge_density(gray)
            if edge_density < self.RAIN_DETECTION_THRESHOLD and mean_brightness < 100:
                return WeatherCondition.RAIN

            if mean_brightness < self.LOW_BRIGHTNESS_THRESHOLD or dark_pixels > 0.4:
                return WeatherCondition.NIGHT
            if bright_pixels > self.BACKLIGHT_RATIO_THRESHOLD and dark_pixels > 0.2:
                return WeatherCondition.BACKLIGHT
            return WeatherCondition.NORMAL
        except Exception as e:
            logger.debug(f"estimate_scene_type failed: {e}")
            return WeatherCondition.NORMAL

    def _estimate_edge_density(self, gray: np.ndarray) -> float:
        """估计图像边缘密度，用于判断雨雾天气"""
        try:
            import cv2
            edges = cv2.Canny(gray, 50, 150)
            edge_pixels = np.sum(edges > 0)
            return edge_pixels / (gray.size + 1e-6)
        except Exception:
            return 1.0

    def analyze_weather(self, frame: np.ndarray, 
                        external_weather: Optional[str] = None) -> Dict[str, Any]:
        """综合分析天气和光照条件
        
        Args:
            frame: 输入图像帧
            external_weather: 外部天气数据 (rain/fog/snow/night 等)
            
        Returns:
            包含场景类型、是否需要增强、推荐算法等信息的字典
        """
        scene_type = self.estimate_scene_type(frame)
        
        needs_enhancement = scene_type != WeatherCondition.NORMAL
        
        if external_weather:
            ext = external_weather.lower()
            if 'rain' in ext or '雨' in ext:
                scene_type = WeatherCondition.RAIN
                needs_enhancement = True
            elif 'fog' in ext or '雾' in ext or 'haze' in ext:
                scene_type = WeatherCondition.FOG
                needs_enhancement = True
            elif 'snow' in ext or '雪' in ext:
                scene_type = WeatherCondition.SNOW
                needs_enhancement = True
            elif 'night' in ext or '夜间' in ext or 'dark' in ext:
                scene_type = WeatherCondition.NIGHT
                needs_enhancement = True
        
        recommended_algorithm = 'clahe_gamma'
        if scene_type in [WeatherCondition.FOG, WeatherCondition.RAIN, WeatherCondition.SNOW]:
            recommended_algorithm = 'defog'
        elif scene_type == WeatherCondition.NIGHT:
            recommended_algorithm = 'retinex'
        elif scene_type == WeatherCondition.BACKLIGHT:
            recommended_algorithm = 'clahe_whitebalance'
        
        return {
            'scene_type': scene_type,
            'needs_enhancement': needs_enhancement,
            'recommended_algorithm': recommended_algorithm,
            'brightness': float(np.mean(frame)) if frame is not None else 0.0,
            'contrast': float(np.std(frame)) if frame is not None else 0.0,
        }

    def enhance(self, frame: np.ndarray, 
                algorithm: Optional[str] = None,
                brightness: float = 1.0,
                contrast: float = 1.0) -> Tuple[np.ndarray, str, float]:
        """增强图像
        
        Args:
            frame: 输入图像
            algorithm: 指定算法 (retinex/defog/clahe_gamma/clahe_whitebalance)，None 则自动选择
            brightness: 亮度调节因子 (0.5-2.0)
            contrast: 对比度调节因子 (0.5-2.0)
            
        Returns:
            (增强后图, 场景类型, 质量评分增量)
        """
        scene = self.estimate_scene_type(frame)
        
        auto_algorithm = algorithm is None
        if auto_algorithm:
            if scene == WeatherCondition.NORMAL:
                if abs(brightness - 1.0) < 0.01 and abs(contrast - 1.0) < 0.01:
                    return frame, scene, 0.0
                algorithm = 'clahe_gamma'
            elif scene in [WeatherCondition.FOG, WeatherCondition.RAIN, WeatherCondition.SNOW]:
                algorithm = 'defog'
            elif scene == WeatherCondition.NIGHT:
                algorithm = 'retinex'
            elif scene == WeatherCondition.BACKLIGHT:
                algorithm = 'clahe_whitebalance'

        try:
            import cv2
        except ImportError:
            logger.warning("cv2 not available, skip image enhancement")
            return frame, scene, 0.0

        try:
            src_mean = float(cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY).mean())
            
            enhanced = self._apply_enhance(frame, algorithm, brightness, contrast)
            
            dst_mean = float(cv2.cvtColor(enhanced, cv2.COLOR_BGR2GRAY).mean())
            dst_std = float(cv2.cvtColor(enhanced, cv2.COLOR_BGR2GRAY).std())
            
            score_gain = (abs(dst_mean - 110.0) - abs(src_mean - 110.0)) + (dst_std - 40) * 0.5
            
            return enhanced, scene, round(score_gain, 2)
        except Exception as e:
            logger.warning(f"enhance failed: {e}")
            return frame, scene, 0.0

    def _apply_enhance(self, frame: np.ndarray, algorithm: str,
                       brightness: float = 1.0, contrast: float = 1.0) -> np.ndarray:
        """应用指定的增强算法"""
        import cv2
        out = frame.copy()

        if algorithm == 'retinex':
            out = self._retinex.enhance(out)
        elif algorithm == 'defog':
            out = self._fog_remover.defog(out)
        elif algorithm == 'clahe_gamma':
            lab = cv2.cvtColor(out, cv2.COLOR_BGR2LAB)
            l, a, b = cv2.split(lab)
            clahe = cv2.createCLAHE(clipLimit=self._clahe_clip,
                                    tileGridSize=(self._clahe_grid, self._clahe_grid))
            l = clahe.apply(l)
            out = cv2.cvtColor(cv2.merge((l, a, b)), cv2.COLOR_LAB2BGR)
            gamma = 0.6 / brightness if brightness > 0 else 0.6
            out = self._gamma_correction(out, gamma)
        elif algorithm == 'clahe_whitebalance':
            lab = cv2.cvtColor(out, cv2.COLOR_BGR2LAB)
            l, a, b = cv2.split(lab)
            clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(4, 4))
            l = clahe.apply(l)
            out = cv2.cvtColor(cv2.merge((l, a, b)), cv2.COLOR_LAB2BGR)
            out = self._white_balance(out)
        
        if abs(brightness - 1.0) > 0.01 or abs(contrast - 1.0) > 0.0:
            out = self._adjust_brightness_contrast(out, brightness, contrast)

        return out

    def _adjust_brightness_contrast(self, img: np.ndarray, 
                                     brightness: float, contrast: float) -> np.ndarray:
        """调整亮度和对比度
        
        Args:
            img: 输入图像
            brightness: 亮度因子 (0.5-2.0, 1.0为原始)
            contrast: 对比度因子 (0.5-2.0, 1.0为原始)
        """
        import cv2
        img_float = img.astype(np.float32)
        
        brightness = np.clip(brightness, 0.5, 2.0)
        contrast = np.clip(contrast, 0.5, 2.0)
        
        mean = np.mean(img_float, axis=(0, 1), keepdims=True)
        img_centered = (img_float - mean) * contrast + mean
        img_brightness = img_centered + (brightness - 1.0) * 128.0
        
        return np.clip(img_brightness, 0, 255).astype(np.uint8)

    def enhance_roi(self, frame: np.ndarray, bbox) -> Tuple[np.ndarray, str, float]:
        """仅增强指定区域（bbox=[x1,y1,x2,y2]），性能更好."""
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
retinex_enhancer = RetinexEnhancer()
fog_remover = FogRemover()

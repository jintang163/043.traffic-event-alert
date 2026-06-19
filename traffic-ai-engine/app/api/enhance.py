import io
import base64
import time
from typing import Optional, Dict, Any

import numpy as np
from fastapi import APIRouter, UploadFile, File, HTTPException, status, Body

from app.config.settings import settings
from app.core.image_enhancer import night_backlight_enhancer
from app.core.stream_processor import stream_processor
from app.schemas.enhance import (
    EnhanceRequest,
    EnhanceResponse,
    WeatherAnalysisRequest,
    WeatherAnalysisResponse,
)

router = APIRouter()


@router.post("/image", response_model=EnhanceResponse)
async def enhance_image(
    file: UploadFile = File(...),
    algorithm: Optional[str] = None,
    brightness: float = 1.0,
    contrast: float = 1.0,
    external_weather: Optional[str] = None,
):
    """上传图像并进行增强处理"""
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="File must be an image"
        )

    start_time = time.time()

    try:
        from PIL import Image
        contents = await file.read()
        image = np.array(Image.open(io.BytesIO(contents)).convert("RGB"))
        import cv2
        image = cv2.cvtColor(image, cv2.COLOR_RGB2BGR)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Failed to parse image: {e}"
        )

    try:
        weather_analysis = night_backlight_enhancer.analyze_weather(image, external_weather)
        
        algo = algorithm
        if algo == "auto":
            algo = None
        
        enhanced_image, scene_type, score_gain = night_backlight_enhancer.enhance(
            image,
            algorithm=algo,
            brightness=brightness,
            contrast=contrast,
        )

        import cv2
        _, buffer = cv2.imencode(".jpg", enhanced_image)
        enhanced_base64 = base64.b64encode(buffer).decode("utf-8")

        processing_time = time.time() - start_time

        return EnhanceResponse(
            success=True,
            scene_type=weather_analysis['scene_type'],
            algorithm_used=algo or weather_analysis['recommended_algorithm'],
            needs_enhancement=weather_analysis['needs_enhancement'],
            score_gain=score_gain,
            brightness=weather_analysis['brightness'],
            contrast=weather_analysis['contrast'],
            processing_time=round(processing_time, 4),
            enhanced_image_base64=enhanced_base64,
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Enhancement failed: {e}"
        )


@router.post("/analyze", response_model=WeatherAnalysisResponse)
async def analyze_weather(
    file: UploadFile = File(...),
    external_weather: Optional[str] = None,
):
    """分析图像的天气/光照条件，不进行实际增强"""
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="File must be an image"
        )

    start_time = time.time()

    try:
        from PIL import Image
        contents = await file.read()
        image = np.array(Image.open(io.BytesIO(contents)).convert("RGB"))
        import cv2
        image = cv2.cvtColor(image, cv2.COLOR_RGB2BGR)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Failed to parse image: {e}"
        )

    try:
        analysis = night_backlight_enhancer.analyze_weather(image, external_weather)
        processing_time = time.time() - start_time

        return WeatherAnalysisResponse(
            scene_type=analysis['scene_type'],
            needs_enhancement=analysis['needs_enhancement'],
            recommended_algorithm=analysis['recommended_algorithm'],
            brightness=analysis['brightness'],
            contrast=analysis['contrast'],
            processing_time=round(processing_time, 4),
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Analysis failed: {e}"
        )


@router.get("/algorithms")
async def get_available_algorithms():
    """获取可用的增强算法列表"""
    return {
        "algorithms": [
            {
                "name": "auto",
                "description": "自动选择 - 根据场景自动选择最佳算法",
                "scenarios": ["所有场景"]
            },
            {
                "name": "retinex",
                "description": "多尺度 Retinex - 适用于夜间、低光照场景",
                "scenarios": ["夜间", "地下停车场", "隧道"]
            },
            {
                "name": "defog",
                "description": "暗通道去雾 - 适用于雨、雾、雪天气",
                "scenarios": ["雨天", "雾天", "雪天", "阴霾"]
            },
            {
                "name": "clahe_gamma",
                "description": "CLAHE + 伽马校正 - 适用于一般低光照场景",
                "scenarios": ["傍晚", "阴天", "普通低光照"]
            },
            {
                "name": "clahe_whitebalance",
                "description": "CLAHE + 白平衡 - 适用于逆光场景",
                "scenarios": ["逆光", "强对比光照"]
            }
        ],
        "scene_types": [
            {"type": "normal", "description": "正常光照"},
            {"type": "night", "description": "夜间/低光照"},
            {"type": "backlight", "description": "逆光"},
            {"type": "rain", "description": "雨天"},
            {"type": "fog", "description": "雾天"},
            {"type": "snow", "description": "雪天"},
        ]
    }


@router.post("/stream/{camera_id}/config")
async def update_stream_enhancement_config(
    camera_id: int,
    enable_enhancement: Optional[bool] = Body(None, description="是否启用图像增强"),
    auto_trigger: Optional[bool] = Body(None, description="是否自动触发增强"),
    algorithm: Optional[str] = Body(None, description="增强算法"),
    brightness: Optional[float] = Body(None, description="亮度调节 (0.5-2.0)", ge=0.5, le=2.0),
    contrast: Optional[float] = Body(None, description="对比度调节 (0.5-2.0)", ge=0.5, le=2.0),
):
    """动态更新指定摄像头流的图像增强配置"""
    with stream_processor._lock:
        if camera_id not in stream_processor.active_streams:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Camera {camera_id} stream is not active"
            )
        
        stream_info = stream_processor.active_streams[camera_id]
        
        if enable_enhancement is not None:
            stream_info["enable_enhancement"] = enable_enhancement
            stream_info["auto_trigger_enhancement"] = auto_trigger if auto_trigger is not None else enable_enhancement
        
        if auto_trigger is not None:
            stream_info["auto_trigger_enhancement"] = auto_trigger
        
        if algorithm is not None:
            stream_info["enhancement_algorithm"] = algorithm
        
        if brightness is not None:
            stream_info["enhancement_brightness"] = brightness
        
        if contrast is not None:
            stream_info["enhancement_contrast"] = contrast
        
        return {
            "success": True,
            "cameraId": camera_id,
            "enhancement": {
                "enabled": stream_info.get("enable_enhancement", False),
                "autoTrigger": stream_info.get("auto_trigger_enhancement", True),
                "algorithm": stream_info.get("enhancement_algorithm", "auto"),
                "brightness": stream_info.get("enhancement_brightness", 1.0),
                "contrast": stream_info.get("enhancement_contrast", 1.0),
                "sceneType": stream_info.get("current_scene_type", "normal"),
                "active": stream_info.get("enhancement_active", False),
            }
        }


@router.get("/stream/{camera_id}/status")
async def get_stream_enhancement_status(camera_id: int):
    """获取指定摄像头流的图像增强状态"""
    status = stream_processor.get_stream_status(camera_id)
    if not status.get("running", False):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Camera {camera_id} stream is not active"
        )
    
    return status.get("enhancement", {})


@router.get("/config")
async def get_global_enhancement_config():
    """获取全局图像增强配置"""
    return {
        "enabled": settings.IMAGE_ENHANCEMENT_ENABLED,
        "autoTrigger": settings.IMAGE_ENHANCEMENT_AUTO_TRIGGER,
        "defaultAlgorithm": settings.IMAGE_ENHANCEMENT_ALGORITHM,
        "defaultBrightness": settings.IMAGE_ENHANCEMENT_BRIGHTNESS,
        "defaultContrast": settings.IMAGE_ENHANCEMENT_CONTRAST,
        "minBrightness": settings.IMAGE_ENHANCEMENT_MIN_BRIGHTNESS,
        "weatherDataEnabled": settings.WEATHER_DATA_ENABLED,
    }

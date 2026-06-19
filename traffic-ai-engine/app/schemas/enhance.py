from typing import Optional, Dict, Any
from pydantic import BaseModel, Field


class EnhanceRequest(BaseModel):
    """图像增强请求"""
    algorithm: Optional[str] = Field(
        None, 
        description="增强算法: auto/retinex/defog/clahe_gamma/clahe_whitebalance，auto为自动选择"
    )
    brightness: float = Field(
        1.0, 
        description="亮度调节因子 (0.5-2.0), 1.0为原始亮度",
        ge=0.5,
        le=2.0
    )
    contrast: float = Field(
        1.0, 
        description="对比度调节因子 (0.5-2.0), 1.0为原始对比度",
        ge=0.5,
        le=2.0
    )
    external_weather: Optional[str] = Field(
        None, 
        description="外部天气数据: rain/fog/snow/night 等"
    )


class EnhanceResponse(BaseModel):
    """图像增强响应"""
    success: bool = Field(description="是否成功")
    scene_type: str = Field(description="检测到的场景类型: normal/night/backlight/rain/fog/snow")
    algorithm_used: str = Field(description="实际使用的增强算法")
    needs_enhancement: bool = Field(description="是否需要增强")
    score_gain: float = Field(description="质量评分增量")
    brightness: float = Field(description="原始图像平均亮度")
    contrast: float = Field(description="原始图像对比度")
    processing_time: float = Field(description="处理时间（秒）")
    enhanced_image_base64: Optional[str] = Field(None, description="增强后图像的Base64编码")


class WeatherAnalysisRequest(BaseModel):
    """天气分析请求"""
    external_weather: Optional[str] = Field(None, description="外部天气数据")


class WeatherAnalysisResponse(BaseModel):
    """天气分析响应"""
    scene_type: str = Field(description="检测到的场景类型")
    needs_enhancement: bool = Field(description="是否需要增强")
    recommended_algorithm: str = Field(description="推荐的增强算法")
    brightness: float = Field(description="图像平均亮度")
    contrast: float = Field(description="图像对比度")
    processing_time: float = Field(description="处理时间（秒）")

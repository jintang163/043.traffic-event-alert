from typing import List, Optional
from pydantic import BaseModel, Field


class ReIDFeature(BaseModel):
    track_id: int = Field(description="局部追踪ID")
    camera_id: str = Field(description="摄像头ID")
    feature_vector: List[float] = Field(description="ReID特征向量")
    norm_feature: Optional[List[float]] = Field(None, description="L2归一化特征")
    bbox: Optional[List[float]] = Field(None, description="边界框 [x1,y1,x2,y2]")
    class_name: Optional[str] = Field(None, description="目标类别")
    confidence: float = Field(1.0, description="检测置信度")
    timestamp: float = Field(description="时间戳")
    snapshot_url: Optional[str] = Field(None, description="抓拍图")


class LicensePlateResult(BaseModel):
    plate_number: Optional[str] = Field(None, description="车牌号")
    confidence: float = Field(0.0, description="识别置信度")
    plate_color: Optional[str] = Field(None, description="车牌颜色")
    vehicle_color: Optional[str] = Field(None, description="车身颜色")
    vehicle_type: Optional[str] = Field(None, description="车辆类型")
    bbox: Optional[List[float]] = Field(None, description="车牌区域 [x1,y1,x2,y2]")
    scene_type: Optional[str] = Field(None, description="场景类型 normal/night/backlight")
    enhance_gain: Optional[float] = Field(None, description="图像增强增益")
    plate_image_base64: Optional[str] = Field(None, description="车牌截图 Base64")


class MatchResult(BaseModel):
    matched: bool = Field(False, description="是否匹配成功")
    match_score: float = Field(0.0, description="最终匹配置信度")
    match_method: int = Field(0, description="1-车牌 2-ReID 3-联合")
    plate_score: float = Field(0.0, description="车牌匹配置信度")
    reid_score: float = Field(0.0, description="ReID特征相似度")
    global_track_id: Optional[str] = Field(None, description="匹配到的全局轨迹ID")
    global_track_no: Optional[str] = Field(None, description="全局轨迹编号")
    reason: Optional[str] = Field(None, description="匹配说明")


class CrossCameraTrackRequest(BaseModel):
    camera_id: str = Field(description="当前摄像头ID")
    leaving_track_id: int = Field(description="驶出视野的局部追踪ID")
    target_class: Optional[str] = Field(None, description="目标类别")
    license_plate: Optional[LicensePlateResult] = Field(None, description="车牌识别结果")
    reid_feature: Optional[ReIDFeature] = Field(None, description="ReID特征")
    leave_direction: Optional[float] = Field(None, description="驶出方向角")
    leave_time: float = Field(description="驶出时间戳")


class HandoverResult(BaseModel):
    handover: bool = Field(False, description="是否接力成功")
    matched_track_id: Optional[str] = Field(None, description="匹配到的全局轨迹ID")
    target_camera_ids: List[str] = Field(default_factory=list, description="接力追踪的目标摄像头列表")
    confidence: float = Field(0.0, description="接力置信度")
    message: Optional[str] = Field(None)

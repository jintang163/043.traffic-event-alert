from typing import List, Optional
from pydantic import BaseModel, Field

from app.schemas.detection import BBox, Detection


class TrackedObject(BaseModel):
    track_id: int = Field(description="追踪 ID")
    class_id: int = Field(description="类别 ID")
    class_name: str = Field(description="类别名称")
    bbox: BBox = Field(description="当前边界框")
    confidence: float = Field(description="置信度", ge=0.0, le=1.0)
    age: int = Field(description="追踪存在的帧数")
    hits: int = Field(description="连续检测命中次数")
    velocity: Optional[List[float]] = Field(None, description="速度向量 [vx, vy]")


class TrackingRequest(BaseModel):
    frame_url: str = Field(description="帧图片 URL")
    frame_id: str = Field(description="帧 ID")
    reset: Optional[bool] = Field(False, description="是否重置追踪器")
    confidence_threshold: Optional[float] = Field(None, description="置信度阈值", ge=0.0, le=1.0)


class TrackingResponse(BaseModel):
    frame_id: str = Field(description="帧 ID")
    tracked_objects: List[TrackedObject] = Field(description="追踪对象列表")
    processing_time: float = Field(description="处理时间（秒）")


class TrackingResult(BaseModel):
    track_id: int = Field(description="追踪 ID")
    class_id: int = Field(description="类别 ID")
    class_name: str = Field(description="类别名称")
    detections: List[Detection] = Field(description="该对象的检测历史")
    first_seen: str = Field(description="首次出现时间")
    last_seen: str = Field(description="最后出现时间")

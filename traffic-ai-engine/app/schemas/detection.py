from typing import List, Optional
from pydantic import BaseModel, Field


class BBox(BaseModel):
    x1: float = Field(description="左上角 x 坐标")
    y1: float = Field(description="左上角 y 坐标")
    x2: float = Field(description="右下角 x 坐标")
    y2: float = Field(description="右下角 y 坐标")


class Detection(BaseModel):
    class_id: int = Field(description="类别 ID")
    class_name: str = Field(description="类别名称")
    confidence: float = Field(description="置信度", ge=0.0, le=1.0)
    bbox: BBox = Field(description="边界框")


class DetectionRequest(BaseModel):
    image_url: Optional[str] = Field(None, description="图片 URL")
    confidence_threshold: Optional[float] = Field(None, description="置信度阈值", ge=0.0, le=1.0)
    iou_threshold: Optional[float] = Field(None, description="IOU 阈值", ge=0.0, le=1.0)
    classes: Optional[List[int]] = Field(None, description="指定检测的类别 ID 列表")


class DetectionResponse(BaseModel):
    image_id: str = Field(description="图片 ID")
    detections: List[Detection] = Field(description="检测结果列表")
    processing_time: float = Field(description="处理时间（秒）")


class BatchDetectionRequest(BaseModel):
    image_urls: List[str] = Field(description="图片 URL 列表")
    confidence_threshold: Optional[float] = Field(None, description="置信度阈值", ge=0.0, le=1.0)
    iou_threshold: Optional[float] = Field(None, description="IOU 阈值", ge=0.0, le=1.0)
    classes: Optional[List[int]] = Field(None, description="指定检测的类别 ID 列表")


class BatchDetectionResponse(BaseModel):
    results: List[DetectionResponse] = Field(description="批量检测结果")
    total_processing_time: float = Field(description="总处理时间（秒）")

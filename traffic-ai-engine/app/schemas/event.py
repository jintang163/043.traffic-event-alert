from datetime import datetime
from typing import List, Optional, Dict, Any
from enum import Enum
from pydantic import BaseModel, Field

from app.schemas.reid import LicensePlateResult
from app.schemas.tracking import TrackedObject


class EventType(str, Enum):
    ACCIDENT = "ACCIDENT"
    REVERSE = "REVERSE"
    DEBRIS = "DEBRIS"
    INTRUSION = "INTRUSION"
    SPEEDING = "SPEEDING"
    RED_LIGHT = "RED_LIGHT"
    WRONG_WAY = "WRONG_WAY"
    ILLEGAL_PARKING = "ILLEGAL_PARKING"
    PEDESTRIAN_CROSSING = "PEDESTRIAN_CROSSING"
    PEDESTRIAN_INTRUSION = "PEDESTRIAN_INTRUSION"
    TRAFFIC_CONGESTION = "TRAFFIC_CONGESTION"
    STOPPED_VEHICLE = "STOPPED_VEHICLE"
    CONGESTION = "CONGESTION"
    CONE_MISSING = "CONE_MISSING"
    CONSTRUCTION_SPEEDING = "CONSTRUCTION_SPEEDING"
    CONSTRUCTION_INTRUSION = "CONSTRUCTION_INTRUSION"
    HORN = "HORN"
    COLLISION_SOUND = "COLLISION_SOUND"
    SIREN = "SIREN"


class EventSeverity(int, Enum):
    LOW = 1
    MEDIUM = 2
    HIGH = 3
    CRITICAL = 4


class EventLocation(BaseModel):
    latitude: Optional[float] = Field(None, description="纬度")
    longitude: Optional[float] = Field(None, description="经度")
    camera_id: Optional[str] = Field(None, description="摄像头 ID")
    region: Optional[str] = Field(None, description="区域名称")


class TrafficEvent(BaseModel):
    event_id: str = Field(description="事件 ID")
    event_type: EventType = Field(description="事件类型")
    severity: EventSeverity = Field(description="事件严重程度")
    timestamp: datetime = Field(description="事件发生时间")
    location: EventLocation = Field(description="事件位置")
    description: str = Field(description="事件描述")
    involved_objects: List[TrackedObject] = Field(description="涉及的追踪对象")
    confidence: float = Field(description="事件置信度", ge=0.0, le=1.0)
    metadata: Optional[Dict[str, Any]] = Field(None, description="额外元数据")
    snapshot_base64: Optional[str] = Field(None, description="事件快照 Base64")
    license_plates: Optional[List[LicensePlateResult]] = Field(None, description="车牌识别结果")


class EventDetectionRequest(BaseModel):
    tracked_objects: List[TrackedObject] = Field(description="追踪对象列表")
    tracks: Optional[List[Dict[str, Any]]] = Field(None, description="轨迹数据")
    frame_id: str = Field(description="帧 ID")
    camera_id: Optional[str] = Field(None, description="摄像头 ID")
    location: Optional[EventLocation] = Field(None, description="位置信息")
    event_types: Optional[List[EventType]] = Field(None, description="指定检测的事件类型")


class EventDetectionResponse(BaseModel):
    frame_id: str = Field(description="帧 ID")
    events: List[TrafficEvent] = Field(description="检测到的事件列表")
    processing_time: float = Field(description="处理时间（秒）")


class EventQueryRequest(BaseModel):
    event_type: Optional[EventType] = Field(None, description="事件类型")
    severity: Optional[EventSeverity] = Field(None, description="严重程度")
    start_time: Optional[datetime] = Field(None, description="开始时间")
    end_time: Optional[datetime] = Field(None, description="结束时间")
    camera_id: Optional[str] = Field(None, description="摄像头 ID")
    limit: Optional[int] = Field(100, description="返回结果数量限制")


class EventQueryResponse(BaseModel):
    events: List[TrafficEvent] = Field(description="事件列表")
    total: int = Field(description="总事件数")


class StreamDetectionRequest(BaseModel):
    cameraId: int = Field(description="摄像头ID")
    streamUrl: Optional[str] = Field(None, description="视频流地址")
    fps: int = Field(2, description="抽帧频率(帧/秒)")
    enableTrack: bool = Field(True, description="是否启用目标跟踪")
    enableEvent: bool = Field(True, description="是否启用事件检测")
    enableEnhancement: Optional[bool] = Field(None, description="是否启用图像增强，None则使用系统配置")
    enhancementAlgorithm: Optional[str] = Field(None, description="增强算法: auto/retinex/defog/clahe_gamma/clahe_whitebalance")
    brightness: Optional[float] = Field(None, description="亮度调节 (0.5-2.0)", ge=0.5, le=2.0)
    contrast: Optional[float] = Field(None, description="对比度调节 (0.5-2.0)", ge=0.5, le=2.0)


class AiEventCallbackRequest(BaseModel):
    eventNo: Optional[str] = None
    cameraId: int
    eventType: str
    eventLevel: int = 1
    confidence: float
    eventTime: Optional[datetime] = None
    description: str
    snapshotBase64: Optional[str] = None
    eventVideo: Optional[str] = None
    trackData: Optional[List[Dict[str, Any]]] = None
    bbox: Optional[Dict[str, Any]] = None
    licensePlates: Optional[List[Dict[str, Any]]] = None
    metadata: Optional[Dict[str, Any]] = Field(None, description="事件元数据，用于关联施工计划等")


class ConeDetectionCallbackRequest(BaseModel):
    planId: Optional[int] = None
    planCode: Optional[str] = None
    planName: Optional[str] = None
    cameraId: Optional[int] = None
    detectionTime: datetime
    detectedConeCount: int
    standardConeCount: int
    missingConeCount: int
    isCompliant: int
    complianceRate: float
    avgConfidence: float
    alertTriggered: int
    alertLevel: Optional[int] = None
    conePositions: Optional[str] = None
    description: Optional[str] = None


class ConstructionPlanConfigRequest(BaseModel):
    cameraId: int
    planConfig: Dict[str, Any]


class AudioEventCallbackRequest(BaseModel):
    eventNo: Optional[str] = None
    cameraId: Optional[int] = None
    eventType: str = Field(description="音频事件类型: HORN/COLLISION_SOUND/SIREN")
    confidence: float = Field(description="置信度")
    duration: float = Field(description="持续时间(秒)")
    peakDb: Optional[float] = Field(None, description="峰值分贝")
    avgDb: Optional[float] = Field(None, description="平均分贝")
    dominantFreq: Optional[float] = Field(None, description="主频率(Hz)")
    eventTime: Optional[datetime] = None
    description: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None

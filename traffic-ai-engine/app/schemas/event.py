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
    SPEEDING = "speeding"
    RED_LIGHT = "red_light"
    WRONG_WAY = "wrong_way"
    ILLEGAL_PARKING = "illegal_parking"
    PEDESTRIAN_CROSSING = "pedestrian_crossing"
    TRAFFIC_CONGESTION = "traffic_congestion"


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

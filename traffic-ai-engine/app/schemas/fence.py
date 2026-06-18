from typing import List, Optional
from pydantic import BaseModel, Field


class FenceConfig(BaseModel):
    fence_id: str = Field(description="围栏ID")
    fence_code: str = Field(description="围栏编号")
    fence_name: str = Field(description="围栏名称")
    fence_type: int = Field(1, description="围栏类型：1-施工区 2-应急车道 3-禁入区 4-自定义")
    camera_id: Optional[int] = Field(None, description="关联摄像头ID")
    polygon_points_pixel: List[List[float]] = Field(
        default_factory=list,
        description="归一化像素坐标 [[nx,ny],...] 值0~1，用于AI检测"
    )
    polygon_points: List[List[float]] = Field(
        default_factory=list,
        description="GIS坐标 [[lng,lat],...]，用于地图展示（可选）"
    )
    center_lng: Optional[float] = Field(None, description="中心点经度")
    center_lat: Optional[float] = Field(None, description="中心点纬度")
    area: Optional[float] = Field(None, description="面积 平方米")
    alert_enabled: bool = Field(True, description="是否启用告警")
    alert_level: int = Field(2, description="告警级别 1-一般 2-严重 3-紧急 4-特急")
    detect_target_types: List[str] = Field(default_factory=list, description="检测目标类型")
    stay_seconds: int = Field(0, description="停留多久触发告警，0表示立即触发")
    cooldown_seconds: int = Field(60, description="告警冷却时间 秒")
    notify_enabled: bool = Field(True, description="是否启用通知")
    link_work_order: bool = Field(False, description="是否自动联动工单")
    color: str = Field("#ff4d4f", description="围栏显示颜色")
    description: Optional[str] = Field(None, description="描述")

    class Config:
        from_attributes = True

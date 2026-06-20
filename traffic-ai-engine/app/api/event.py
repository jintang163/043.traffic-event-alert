import time
from datetime import datetime
from typing import List, Optional

from fastapi import APIRouter, HTTPException, status

from app.config.settings import settings
from app.core.event_analyzer import event_analyzer
from app.core.tracker import get_tracker
from app.schemas.event import (
    EventDetectionRequest,
    EventDetectionResponse,
    EventQueryRequest,
    EventQueryResponse,
    TrafficEvent,
    EventType,
    EventSeverity,
    ConstructionPlanConfigRequest,
)

router = APIRouter()

detected_events: List[TrafficEvent] = []


@router.post("/analyze", response_model=EventDetectionResponse)
async def analyze_events(request: EventDetectionRequest):
    start_time = time.time()

    result = event_analyzer.analyze(
        camera_id=request.camera_id or request.frame_id,
        tracked_objects=request.tracked_objects
    )

    events = result[0] if isinstance(result, tuple) else result
    cone_detection = result[1] if isinstance(result, tuple) and len(result) > 1 else None

    detected_events.extend(events)
    if len(detected_events) > 1000:
        detected_events[:] = detected_events[-1000:]

    processing_time = time.time() - start_time

    return EventDetectionResponse(
        frame_id=request.frame_id,
        events=events,
        processing_time=round(processing_time, 4),
        extra={"cone_detection": cone_detection} if cone_detection else None
    )


@router.post("/query", response_model=EventQueryResponse)
async def query_events(request: EventQueryRequest):
    filtered_events = detected_events

    if request.event_type:
        filtered_events = [
            e for e in filtered_events
            if e.event_type == request.event_type
        ]

    if request.severity:
        filtered_events = [
            e for e in filtered_events
            if e.severity == request.severity
        ]

    if request.start_time:
        filtered_events = [
            e for e in filtered_events
            if e.timestamp >= request.start_time
        ]

    if request.end_time:
        filtered_events = [
            e for e in filtered_events
            if e.timestamp <= request.end_time
        ]

    if request.camera_id:
        filtered_events = [
            e for e in filtered_events
            if e.location and e.location.camera_id == request.camera_id
        ]

    total = len(filtered_events)

    if request.limit and request.limit > 0:
        filtered_events = filtered_events[-request.limit:]

    return EventQueryResponse(
        events=list(reversed(filtered_events)),
        total=total
    )


@router.get("/types")
async def get_event_types():
    return {
        "event_types": [
            {"value": e.value, "label": {
                "ACCIDENT": "交通事故",
                "REVERSE": "车辆逆行",
                "DEBRIS": "路面抛洒物",
                "speeding": "超速行驶",
                "red_light": "闯红灯",
                "wrong_way": "逆行",
                "illegal_parking": "违法停车",
                "pedestrian_crossing": "行人横穿",
                "traffic_congestion": "交通拥堵"
            }.get(e.value, e.value)}
            for e in EventType
        ],
        "severities": [
            {"value": s.value, "label": {
                1: "一般",
                2: "严重",
                3: "紧急",
                4: "特急"
            }.get(s.value, s.name)}
            for s in EventSeverity
        ],
        "confidence_threshold": settings.EVENT_CONFIDENCE_THRESHOLD
    }


@router.get("/{event_id}", response_model=TrafficEvent)
async def get_event(event_id: str):
    for event in detected_events:
        if event.event_id == event_id:
            return event

    raise HTTPException(
        status_code=status.HTTP_404_NOT_FOUND,
        detail=f"Event {event_id} not found"
    )


@router.get("/", response_model=EventQueryResponse)
async def list_events(
    event_type: Optional[EventType] = None,
    severity: Optional[EventSeverity] = None,
    start_time: Optional[datetime] = None,
    end_time: Optional[datetime] = None,
    camera_id: Optional[str] = None,
    limit: Optional[int] = 100
):
    request = EventQueryRequest(
        event_type=event_type,
        severity=severity,
        start_time=start_time,
        end_time=end_time,
        camera_id=camera_id,
        limit=limit
    )
    return await query_events(request)


@router.delete("/{event_id}")
async def delete_event(event_id: str):
    global detected_events

    for i, event in enumerate(detected_events):
        if event.event_id == event_id:
            del detected_events[i]
            return {"status": "success", "message": f"Event {event_id} deleted"}

    raise HTTPException(
        status_code=status.HTTP_404_NOT_FOUND,
        detail=f"Event {event_id} not found"
    )


@router.get("/statistics/summary")
async def get_event_statistics():
    stats = {
        "total_events": len(detected_events),
        "by_type": {},
        "by_severity": {},
        "last_hour": 0,
        "last_24h": 0
    }

    now = datetime.now()
    one_hour_ago = now.timestamp() - 3600
    one_day_ago = now.timestamp() - 86400

    for event in detected_events:
        et = event.event_type.value
        stats["by_type"][et] = stats["by_type"].get(et, 0) + 1

        sv = event.severity.value
        sv_label = {1: "low", 2: "medium", 3: "high", 4: "critical"}.get(sv, str(sv))
        stats["by_severity"][sv_label] = stats["by_severity"].get(sv_label, 0) + 1

        event_ts = event.timestamp.timestamp()
        if event_ts >= one_hour_ago:
            stats["last_hour"] += 1
        if event_ts >= one_day_ago:
            stats["last_24h"] += 1

    return stats


@router.post("/pedestrian-intrusion/road-region/{camera_id}")
async def set_pedestrian_road_region(camera_id: str, request: dict):
    from typing import List, Tuple
    road_region_pixel = request.get("roadRegionPixel", "")
    if not road_region_pixel:
        event_analyzer.clear_road_region(camera_id)
        return {"status": "success", "message": f"Camera {camera_id} road region cleared", "camera_id": camera_id}

    import json
    try:
        points = json.loads(road_region_pixel)
        region = [(float(p[0]), float(p[1])) for p in points]
        event_analyzer.set_road_region(camera_id, region)
        return {
            "status": "success",
            "message": f"Camera {camera_id} road region updated",
            "camera_id": camera_id,
            "points_count": len(region)
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid road region format: {str(e)}")


@router.get("/pedestrian-intrusion/road-region/{camera_id}")
async def get_pedestrian_road_region(camera_id: str):
    region = event_analyzer.get_road_region(camera_id)
    if region is None:
        return {"status": "success", "camera_id": camera_id, "road_region": None, "is_default": True}
    return {
        "status": "success",
        "camera_id": camera_id,
        "road_region": [[p[0], p[1]] for p in region],
        "is_default": False
    }


@router.post("/construction-plan/sync")
async def sync_construction_plan(request: ConstructionPlanConfigRequest):
    try:
        camera_id = str(request.cameraId)
        plan_config = request.planConfig

        if not plan_config or plan_config.get("plan_status") != 2:
            event_analyzer.remove_construction_plan(camera_id)
            return {
                "status": "success",
                "message": f"Camera {camera_id} construction plan removed (not active)",
                "camera_id": camera_id
            }

        event_analyzer.set_construction_plan(camera_id, plan_config)

        return {
            "status": "success",
            "message": f"Camera {camera_id} construction plan synced",
            "camera_id": camera_id,
            "plan_name": plan_config.get("plan_name"),
            "standard_cone_count": plan_config.get("standard_cone_count", 0)
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to sync construction plan: {str(e)}")


@router.delete("/construction-plan/{camera_id}")
async def remove_construction_plan(camera_id: str):
    try:
        event_analyzer.remove_construction_plan(camera_id)
        return {
            "status": "success",
            "message": f"Camera {camera_id} construction plan removed",
            "camera_id": camera_id
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to remove construction plan: {str(e)}")


@router.get("/construction-plan/{camera_id}")
async def get_construction_plan(camera_id: str):
    plan = event_analyzer.get_construction_plan(camera_id)
    return {
        "status": "success",
        "camera_id": camera_id,
        "plan_config": plan,
        "has_plan": plan is not None
    }

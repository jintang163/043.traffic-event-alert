from typing import List, Optional

from fastapi import APIRouter, HTTPException, Query, status

from app.core.cross_camera_tracker import cross_camera_tracker
from app.schemas.reid import (
    ReIDFeature, LicensePlateResult, MatchResult,
    CrossCameraTrackRequest, HandoverResult
)

router = APIRouter()


@router.post("/register", response_model=str)
async def register_track(
    camera_id: str,
    local_track_id: int,
    class_name: str = "car",
    confidence: float = 1.0,
    feature: Optional[List[float]] = None,
    plate: Optional[LicensePlateResult] = None,
):
    global_id = cross_camera_tracker.register_track(
        camera_id=camera_id,
        local_track_id=local_track_id,
        class_name=class_name,
        reid_feature=feature,
        confidence=confidence,
        license_plate=plate
    )
    return global_id


@router.post("/match", response_model=MatchResult)
async def find_matching_track(req: CrossCameraTrackRequest):
    plate = req.license_plate.plate_number if req.license_plate else None
    feature = req.reid_feature.feature_vector if req.reid_feature else None
    result = cross_camera_tracker.find_matching_track(
        camera_id=req.camera_id,
        target_class=req.target_class,
        plate_number=plate,
        reid_feature=feature
    )
    return result


@router.post("/handover", response_model=HandoverResult)
async def handle_cross_camera_handover(req: CrossCameraTrackRequest):
    result = cross_camera_tracker.handle_cross_camera(req)
    return result


@router.post("/neighbors/{camera_id}")
async def set_camera_neighbors(camera_id: str, neighbor_ids: List[str]):
    cross_camera_tracker.set_camera_neighbors(camera_id, neighbor_ids)
    return {"status": "success", "camera_id": camera_id, "neighbors": neighbor_ids}


@router.get("/neighbors/{camera_id}")
async def get_camera_neighbors(camera_id: str):
    return {
        "camera_id": camera_id,
        "neighbors": cross_camera_tracker.get_camera_neighbors(camera_id)
    }


@router.get("/active")
async def list_active_tracks(camera_id: Optional[str] = None):
    tracks = []
    for gid, rec in cross_camera_tracker._active_tracks.items():
        if camera_id and rec.camera_id != camera_id:
            continue
        tracks.append({
            "global_track_id": rec.global_track_id,
            "camera_id": rec.camera_id,
            "local_track_id": rec.local_track_id,
            "class_name": rec.class_name,
            "plate_number": rec.plate_number,
            "vehicle_color": rec.vehicle_color,
            "vehicle_type": rec.vehicle_type,
            "first_seen": rec.first_seen,
            "last_seen": rec.last_seen,
            "direction": rec.direction,
            "speed": rec.speed,
            "is_event_target": rec.is_event_target,
            "linked_event_count": rec.linked_event_count,
        })
    return {"count": len(tracks), "tracks": tracks}


@router.post("/cleanup")
async def cleanup_expired_tracks():
    before = len(cross_camera_tracker._active_tracks)
    cross_camera_tracker.cleanup_expired()
    after = len(cross_camera_tracker._active_tracks)
    return {"cleaned": before - after, "remaining": after}


@router.get("/handover-history")
async def get_handover_history(limit: int = Query(100, ge=1, le=1000)):
    return {
        "count": min(limit, len(cross_camera_tracker._track_handover_history)),
        "history": cross_camera_tracker._track_handover_history[-limit:]
    }

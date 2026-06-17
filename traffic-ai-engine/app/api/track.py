import io
import time
from typing import Dict, List

import numpy as np
from fastapi import APIRouter, HTTPException, status, UploadFile, File

from PIL import Image
from app.config.settings import settings
from app.core.detector import detector
from app.core.tracker import get_tracker, reset_tracker, tracker_manager
from app.schemas.tracking import (
    TrackingRequest,
    TrackingResponse,
    TrackedObject,
    TrackingResult
)

router = APIRouter()


@router.post("/frame")
async def track_objects_in_frame(
    camera_id: str,
    file: UploadFile = File(...),
    reset: bool = False,
    confidence_threshold: float = None
):
    start_time = time.time()
    frame_id = camera_id

    if reset:
        reset_tracker(camera_id)

    try:
        contents = await file.read()
        image = np.array(Image.open(io.BytesIO(contents)).convert("RGB"))
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Failed to parse image: {e}"
        )

    detections = detector.detect(image, confidence_threshold=confidence_threshold)
    tracker = get_tracker(camera_id)
    tracked_objects = tracker.update(detections)

    processing_time = time.time() - start_time

    return TrackingResponse(
        frame_id=frame_id,
        tracked_objects=tracked_objects,
        processing_time=round(processing_time, 4)
    )


@router.post("/", response_model=TrackingResponse)
async def track_objects(request: TrackingRequest):
    start_time = time.time()

    if request.reset:
        reset_tracker(request.frame_id)

    image = np.random.randint(0, 256, (720, 1280, 3), dtype=np.uint8)
    detections = detector.detect(image, confidence_threshold=request.confidence_threshold)
    tracker = get_tracker(request.frame_id)
    tracked_objects = tracker.update(detections)

    processing_time = time.time() - start_time
    return TrackingResponse(
        frame_id=request.frame_id,
        tracked_objects=tracked_objects,
        processing_time=round(processing_time, 4)
    )


@router.get("/{camera_id}/objects")
async def get_tracked_objects(camera_id: str):
    if camera_id not in tracker_manager:
        return {
            "camera_id": camera_id,
            "tracked_objects": [],
            "count": 0
        }

    tracker = tracker_manager[camera_id]
    tracked_objects = [t.to_tracked_object() for t in tracker.get_all_tracks().values()]
    return {
        "camera_id": camera_id,
        "tracked_objects": tracked_objects,
        "count": len(tracked_objects)
    }


@router.get("/{camera_id}/objects/{track_id}", response_model=TrackedObject)
async def get_tracked_object(camera_id: str, track_id: int):
    if camera_id not in tracker_manager:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"No tracking data found for camera: {camera_id}"
        )

    tracker = tracker_manager[camera_id]
    track = tracker.get_track(track_id)
    if track is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Track ID {track_id} not found"
        )

    return track.to_tracked_object()


@router.delete("/{camera_id}")
async def reset_tracker_api(camera_id: str):
    reset_tracker(camera_id)
    return {"status": "success", "message": f"Tracker for camera {camera_id} has been reset"}


@router.get("/{camera_id}/history", response_model=List[TrackingResult])
async def get_tracking_history(camera_id: str):
    if camera_id not in tracker_manager:
        return []

    tracker = tracker_manager[camera_id]
    results = []
    for track_id, track in tracker.get_all_tracks().items():
        results.append(
            TrackingResult(
                track_id=track_id,
                class_id=track.class_id,
                class_name=track.class_name,
                detections=[],
                first_seen=time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(track.first_seen)),
                last_seen=time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(track.last_seen))
            )
        )
    return results

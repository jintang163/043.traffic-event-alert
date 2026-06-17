import io
import time
import uuid
from typing import Optional

import numpy as np
from fastapi import APIRouter, UploadFile, File, HTTPException, status

from app.config.settings import settings
from app.core.detector import detector, ObjectDetector
from app.core.tracker import get_tracker
from app.core.event_analyzer import event_analyzer
from app.core.stream_processor import stream_processor
from app.schemas.detection import (
    DetectionRequest,
    DetectionResponse,
    BatchDetectionRequest,
    BatchDetectionResponse,
    Detection,
    BBox
)
from app.schemas.event import StreamDetectionRequest

router = APIRouter()


@router.post("/image", response_model=DetectionResponse)
async def detect_objects_upload(
    file: UploadFile = File(...),
    confidence_threshold: Optional[float] = None,
    iou_threshold: Optional[float] = None
):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="File must be an image"
        )

    start_time = time.time()
    image_id = str(uuid.uuid4())

    try:
        from PIL import Image
        contents = await file.read()
        image = np.array(Image.open(io.BytesIO(contents)).convert("RGB"))
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Failed to parse image: {e}"
        )

    detections = detector.detect(image, confidence_threshold, iou_threshold)
    processing_time = time.time() - start_time

    return DetectionResponse(
        image_id=image_id,
        detections=detections,
        processing_time=round(processing_time, 4)
    )


@router.post("/stream")
async def start_stream_detection(request: StreamDetectionRequest):
    result = stream_processor.start_stream(
        camera_id=request.cameraId,
        stream_url=request.streamUrl,
        fps=request.fps,
        enable_track=request.enableTrack,
        enable_event=request.enableEvent
    )
    return result


@router.post("/stream/{camera_id}/stop")
async def stop_stream_detection(camera_id: int):
    result = stream_processor.stop_stream(camera_id)
    return result


@router.get("/stream/status")
async def get_stream_status(camera_id: Optional[int] = None):
    return stream_processor.get_stream_status(camera_id)


@router.post("/", response_model=DetectionResponse)
async def detect_objects(request: DetectionRequest):
    start_time = time.time()
    image_id = str(uuid.uuid4())

    image = np.random.randint(0, 256, (720, 1280, 3), dtype=np.uint8)
    detections = detector.detect(image, request.confidence_threshold, request.iou_threshold, request.classes)

    processing_time = time.time() - start_time
    return DetectionResponse(
        image_id=image_id,
        detections=detections,
        processing_time=round(processing_time, 4)
    )


@router.post("/batch", response_model=BatchDetectionResponse)
async def detect_objects_batch(request: BatchDetectionRequest):
    start_time = time.time()
    results = []

    for image_url in request.image_urls:
        image_id = str(uuid.uuid4())
        image = np.random.randint(0, 256, (720, 1280, 3), dtype=np.uint8)
        detections = detector.detect(image, request.confidence_threshold, request.iou_threshold, request.classes)
        results.append(
            DetectionResponse(
                image_id=image_id,
                detections=detections,
                processing_time=0.0
            )
        )

    total_processing_time = time.time() - start_time
    return BatchDetectionResponse(
        results=results,
        total_processing_time=round(total_processing_time, 4)
    )


@router.get("/classes")
async def get_detection_classes():
    return {"classes": ObjectDetector.CLASS_NAMES}

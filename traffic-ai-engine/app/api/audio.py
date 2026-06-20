from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional, Dict, Any

from app.core.audio_detector import audio_detector
from app.config.settings import settings

router = APIRouter()


class AudioStreamRequest(BaseModel):
    cameraId: str = Field(description="摄像头ID")
    deviceIndex: Optional[int] = Field(None, description="音频设备索引，None则使用默认设备")
    streamUrl: Optional[str] = Field(None, description="音频流地址（可选）")


class AudioChunkRequest(BaseModel):
    cameraId: str = Field(description="摄像头ID")
    audioDataBase64: str = Field(description="Base64编码的音频数据")
    sampleRate: Optional[int] = Field(None, description="采样率，默认使用系统配置")


@router.post("/start")
async def start_audio_detection(request: AudioStreamRequest):
    if not settings.AUDIO_DETECTION_ENABLED:
        raise HTTPException(status_code=400, detail="Audio detection is not enabled")
    result = audio_detector.start_audio_stream(
        camera_id=request.cameraId,
        device_index=request.deviceIndex,
        stream_url=request.streamUrl,
    )
    return result


@router.post("/stop/{camera_id}")
async def stop_audio_detection(camera_id: str):
    return audio_detector.stop_audio_stream(camera_id)


@router.post("/chunk")
async def process_audio_chunk(request: AudioChunkRequest):
    import base64
    import numpy as np

    if not settings.AUDIO_DETECTION_ENABLED:
        raise HTTPException(status_code=400, detail="Audio detection is not enabled")

    try:
        audio_bytes = base64.b64decode(request.audioDataBase64)
        audio_data = np.frombuffer(audio_bytes, dtype=np.float32)

        events = audio_detector.process_audio_chunk(
            camera_id=request.cameraId,
            audio_data=audio_data,
            sample_rate=request.sampleRate or settings.AUDIO_SAMPLE_RATE,
        )

        return {
            "success": True,
            "cameraId": request.cameraId,
            "detectedEvents": [e.to_dict() for e in events],
            "eventCount": len(events),
        }
    except Exception as e:
        return {"success": False, "message": str(e)}


@router.get("/status/{camera_id}")
async def get_audio_status(camera_id: str):
    return audio_detector.get_stream_status(camera_id)


@router.get("/status")
async def get_all_audio_status():
    return audio_detector.get_stream_status()


@router.get("/config")
async def get_audio_config():
    return {
        "enabled": settings.AUDIO_DETECTION_ENABLED,
        "sampleRate": settings.AUDIO_SAMPLE_RATE,
        "chunkSize": settings.AUDIO_CHUNK_SIZE,
        "channels": settings.AUDIO_CHANNELS,
        "hornMinDuration": settings.AUDIO_HORN_MIN_DURATION,
        "hornMinDb": settings.AUDIO_HORN_MIN_DB,
        "collisionMinDb": settings.AUDIO_COLLISION_MIN_DB,
        "eventCooldown": settings.AUDIO_EVENT_COOLDOWN,
    }

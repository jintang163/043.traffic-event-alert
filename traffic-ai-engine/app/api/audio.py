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
    thresholds = audio_detector.get_threshold_config()
    return {
        "enabled": settings.AUDIO_DETECTION_ENABLED,
        "sampleRate": settings.AUDIO_SAMPLE_RATE,
        "chunkSize": settings.AUDIO_CHUNK_SIZE,
        "channels": settings.AUDIO_CHANNELS,
        "micArrayStrategy": settings.AUDIO_MIC_ARRAY_STRATEGY,
        "callbackUrl": settings.AUDIO_CALLBACK_URL,
        "ambientUpdateAlpha": settings.AUDIO_AMBIENT_UPDATE_ALPHA,
        "eventCooldown": settings.AUDIO_EVENT_COOLDOWN,
        "thresholds": thresholds,
    }


class AudioConfigUpdateRequest(BaseModel):
    enabled: Optional[bool] = None
    sampleRate: Optional[int] = None
    channels: Optional[int] = None
    micArrayStrategy: Optional[str] = None
    hornMinDuration: Optional[float] = None
    hornMinDb: Optional[float] = None
    hornDbAboveAmbient: Optional[float] = None
    hornBandRatio: Optional[float] = None
    collisionMinDb: Optional[float] = None
    collisionDbAboveAmbient: Optional[float] = None
    collisionImpulseMaxRise: Optional[float] = None
    collisionRiseFallRatio: Optional[float] = None
    sirenMinDuration: Optional[float] = None
    sirenDbAboveAmbient: Optional[float] = None
    sirenBandRatio: Optional[float] = None
    eventCooldown: Optional[float] = None
    ambientUpdateAlpha: Optional[float] = None


@router.put("/config")
async def update_audio_config(request: AudioConfigUpdateRequest):
    updated: Dict[str, Any] = {}

    if request.enabled is not None and settings.AUDIO_DETECTION_ENABLED != request.enabled:
        settings.AUDIO_DETECTION_ENABLED = request.enabled
        updated["enabled"] = request.enabled

    if request.sampleRate:
        audio_detector.sample_rate = request.sampleRate
        updated["sampleRate"] = request.sampleRate

    if request.channels:
        audio_detector.channels = request.channels
        updated["channels"] = request.channels

    if request.micArrayStrategy:
        audio_detector.mic_array_strategy = request.micArrayStrategy
        updated["micArrayStrategy"] = request.micArrayStrategy

    if request.hornMinDuration is not None:
        audio_detector.horn_min_duration = request.hornMinDuration
        updated["hornMinDuration"] = request.hornMinDuration

    if request.hornMinDb is not None:
        audio_detector.horn_min_db = request.hornMinDb
        updated["hornMinDb"] = request.hornMinDb

    if request.hornDbAboveAmbient is not None:
        audio_detector.horn_db_above_ambient = request.hornDbAboveAmbient
        updated["hornDbAboveAmbient"] = request.hornDbAboveAmbient

    if request.hornBandRatio is not None:
        audio_detector.horn_band_ratio = request.hornBandRatio
        updated["hornBandRatio"] = request.hornBandRatio

    if request.collisionMinDb is not None:
        audio_detector.collision_min_db = request.collisionMinDb
        updated["collisionMinDb"] = request.collisionMinDb

    if request.collisionDbAboveAmbient is not None:
        audio_detector.collision_db_above_ambient = request.collisionDbAboveAmbient
        updated["collisionDbAboveAmbient"] = request.collisionDbAboveAmbient

    if request.collisionImpulseMaxRise is not None:
        audio_detector.collision_impulse_duration_max = request.collisionImpulseMaxRise
        updated["collisionImpulseMaxRise"] = request.collisionImpulseMaxRise

    if request.collisionRiseFallRatio is not None:
        audio_detector.collision_rise_fall_ratio = request.collisionRiseFallRatio
        updated["collisionRiseFallRatio"] = request.collisionRiseFallRatio

    if request.sirenMinDuration is not None:
        audio_detector.siren_min_duration = request.sirenMinDuration
        updated["sirenMinDuration"] = request.sirenMinDuration

    if request.sirenDbAboveAmbient is not None:
        audio_detector.siren_db_above_ambient = request.sirenDbAboveAmbient
        updated["sirenDbAboveAmbient"] = request.sirenDbAboveAmbient

    if request.sirenBandRatio is not None:
        audio_detector.siren_band_ratio = request.sirenBandRatio
        updated["sirenBandRatio"] = request.sirenBandRatio

    if request.eventCooldown is not None:
        audio_detector._cooldown_seconds = request.eventCooldown
        updated["eventCooldown"] = request.eventCooldown

    if request.ambientUpdateAlpha is not None:
        audio_detector.ambient_update_alpha = request.ambientUpdateAlpha
        updated["ambientUpdateAlpha"] = request.ambientUpdateAlpha

    return {
        "success": True,
        "message": f"已更新 {len(updated)} 个参数",
        "updated": updated,
        "effective": audio_detector.get_threshold_config(),
    }


import os
from typing import List
from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    PROJECT_NAME: str = Field(default="Traffic AI Engine", env="PROJECT_NAME")
    VERSION: str = Field(default="1.0.0", env="VERSION")
    DESCRIPTION: str = Field(default="Traffic event detection and tracking AI engine", env="DESCRIPTION")

    HOST: str = Field(default="0.0.0.0", env="HOST")
    PORT: int = Field(default=8000, env="PORT")
    DEBUG: bool = Field(default=False, env="DEBUG")

    API_V1_PREFIX: str = Field(default="/api/v1", env="API_V1_PREFIX")

    ALLOWED_ORIGINS: List[str] = Field(default=["*"], env="ALLOWED_ORIGINS")

    MODEL_PATH: str = Field(default="./weights/yolov8n.pt", env="MODEL_PATH")
    MODEL_CONFIDENCE: float = Field(default=0.25, env="MODEL_CONFIDENCE")
    MODEL_IOU: float = Field(default=0.45, env="MODEL_IOU")
    MODEL_DEVICE: str = Field(default="cpu", env="MODEL_DEVICE")

    REDIS_HOST: str = Field(default="localhost", env="REDIS_HOST")
    REDIS_PORT: int = Field(default=6379, env="REDIS_PORT")
    REDIS_DB: int = Field(default=0, env="REDIS_DB")
    REDIS_PASSWORD: str = Field(default="", env="REDIS_PASSWORD")

    TRACKER_MAX_AGE: int = Field(default=30, env="TRACKER_MAX_AGE")
    TRACKER_MIN_HITS: int = Field(default=3, env="TRACKER_MIN_HITS")
    TRACKER_IOU_THRESHOLD: float = Field(default=0.3, env="TRACKER_IOU_THRESHOLD")

    EVENT_CONFIDENCE_THRESHOLD: float = Field(default=0.7, env="EVENT_CONFIDENCE_THRESHOLD")
    EVENT_ACCIDENT_STATIC_FRAMES: int = Field(default=30, env="EVENT_ACCIDENT_STATIC_FRAMES")
    EVENT_REVERSE_MIN_SPEED: float = Field(default=5.0, env="EVENT_REVERSE_MIN_SPEED")
    EVENT_DEBRIS_STATIC_FRAMES: int = Field(default=20, env="EVENT_DEBRIS_STATIC_FRAMES")

    BACKEND_CALLBACK_URL: str = Field(default="http://localhost:8080/api/ai/event/callback", env="BACKEND_CALLBACK_URL")
    BACKEND_VIDEO_UPLOAD_URL: str = Field(default="http://localhost:8080/api/ai/event/upload-video", env="BACKEND_VIDEO_UPLOAD_URL")
    BACKEND_DETECTION_PUSH_URL: str = Field(default="http://localhost:8080/api/ai/detection/push", env="BACKEND_DETECTION_PUSH_URL")
    BACKEND_ENABLE_CALLBACK: bool = Field(default=True, env="BACKEND_ENABLE_CALLBACK")

    VIDEO_PRE_RECORD_SECONDS: int = Field(default=10, env="VIDEO_PRE_RECORD_SECONDS")
    VIDEO_POST_RECORD_SECONDS: int = Field(default=10, env="VIDEO_POST_RECORD_SECONDS")
    VIDEO_TEMP_DIR: str = Field(default="./temp_videos", env="VIDEO_TEMP_DIR")
    DETECTION_PUSH_ENABLED: bool = Field(default=True, env="DETECTION_PUSH_ENABLED")
    DETECTION_PUSH_INTERVAL: float = Field(default=0.5, env="DETECTION_PUSH_INTERVAL")

    BACKEND_TRACK_POINT_URL: str = Field(default="http://localhost:8080/api/tracks/points/batch", env="BACKEND_TRACK_POINT_URL")
    TRACK_POINT_PUSH_INTERVAL: float = Field(default=5.0, env="TRACK_POINT_PUSH_INTERVAL")

    IMAGE_ENHANCEMENT_ENABLED: bool = Field(default=True, env="IMAGE_ENHANCEMENT_ENABLED")
    IMAGE_ENHANCEMENT_AUTO_TRIGGER: bool = Field(default=True, env="IMAGE_ENHANCEMENT_AUTO_TRIGGER")
    IMAGE_ENHANCEMENT_MIN_BRIGHTNESS: float = Field(default=60.0, env="IMAGE_ENHANCEMENT_MIN_BRIGHTNESS")
    IMAGE_ENHANCEMENT_ALGORITHM: str = Field(default="auto", env="IMAGE_ENHANCEMENT_ALGORITHM")
    IMAGE_ENHANCEMENT_BRIGHTNESS: float = Field(default=1.0, env="IMAGE_ENHANCEMENT_BRIGHTNESS")
    IMAGE_ENHANCEMENT_CONTRAST: float = Field(default=1.0, env="IMAGE_ENHANCEMENT_CONTRAST")

    WEATHER_DATA_ENABLED: bool = Field(default=False, env="WEATHER_DATA_ENABLED")
    WEATHER_DATA_API_URL: str = Field(default="http://localhost:8080/api/weather/latest", env="WEATHER_DATA_API_URL")

    class Config:
        env_file = ".env"
        case_sensitive = True


settings = Settings()

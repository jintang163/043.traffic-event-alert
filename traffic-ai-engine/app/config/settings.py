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
    BACKEND_ENABLE_CALLBACK: bool = Field(default=True, env="BACKEND_ENABLE_CALLBACK")

    class Config:
        env_file = ".env"
        case_sensitive = True


settings = Settings()

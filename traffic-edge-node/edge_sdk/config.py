import json
import logging
import os
from dataclasses import asdict, dataclass, field
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)


@dataclass
class EdgeNodeConfig:
    server_url: str = "http://localhost:8080"
    node_code: str = "EDGE-001"
    node_name: str = "边缘节点"
    heartbeat_interval: int = 30
    cache_db_path: str = "./edge_cache.db"
    retry_max: int = 5
    retry_initial_delay: int = 10
    snapshot_dir: str = "./snapshots"
    clip_dir: str = "./clips"
    clip_seconds: int = 10
    detect_fps: int = 2
    model_path: Optional[str] = None
    engine_path: Optional[str] = None
    confidence_threshold: float = 0.5
    cooldown_seconds: float = 5.0
    cameras: List[Dict[str, Any]] = field(default_factory=list)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "EdgeNodeConfig":
        valid_fields = {f: data[f] for f in data if f in cls.__dataclass_fields__}
        config = cls(**valid_fields)
        logger.info(
            f"Config loaded from dict: server_url={config.server_url}, "
            f"node_code={config.node_code}, heartbeat_interval={config.heartbeat_interval}s, "
            f"cameras={len(config.cameras)}"
        )
        return config

    @classmethod
    def from_json_file(cls, file_path: str) -> "EdgeNodeConfig":
        if not os.path.exists(file_path):
            logger.warning(f"Config file not found: {file_path}, using defaults")
            return cls()
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            logger.info(f"Config loaded from file: {file_path}")
            return cls.from_dict(data)
        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse config file {file_path}: {e}, using defaults")
            return cls()
        except Exception as e:
            logger.error(f"Error loading config from {file_path}: {e}, using defaults")
            return cls()

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    def save_to_json_file(self, file_path: str) -> bool:
        try:
            os.makedirs(os.path.dirname(file_path) or ".", exist_ok=True)
            with open(file_path, "w", encoding="utf-8") as f:
                json.dump(self.to_dict(), f, ensure_ascii=False, indent=2)
            logger.info(f"Config saved to file: {file_path}")
            return True
        except Exception as e:
            logger.error(f"Failed to save config to {file_path}: {e}")
            return False

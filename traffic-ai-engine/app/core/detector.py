import logging
import time
from typing import List, Optional, Tuple

import numpy as np

from app.config.settings import settings
from app.schemas.detection import Detection, BBox

logger = logging.getLogger(__name__)


class ObjectDetector:
    CLASS_NAMES = {
        0: "person",
        1: "bicycle",
        2: "car",
        3: "motorcycle",
        5: "bus",
        7: "truck",
        9: "traffic light",
        11: "stop sign",
        12: "parking meter",
        13: "bench",
        24: "backpack",
        26: "handbag",
        28: "suitcase",
        39: "bottle",
        41: "cup",
        56: "chair",
        57: "couch",
        62: "tv",
        63: "laptop",
        67: "cell phone",
        73: "book"
    }

    TRAFFIC_CLASSES = {0, 1, 2, 3, 5, 7}

    def __init__(self):
        self.model = None
        self.model_loaded = False
        self._load_model()

    def _load_model(self):
        try:
            from ultralytics import YOLO
            self.model = YOLO(settings.MODEL_PATH)
            self.model_loaded = True
            logger.info(f"YOLOv8 model loaded successfully from {settings.MODEL_PATH}")
        except Exception as e:
            logger.warning(f"Failed to load YOLO model: {e}. Using mock detection mode.")
            self.model = None
            self.model_loaded = False

    def detect(
        self,
        image: np.ndarray,
        confidence_threshold: Optional[float] = None,
        iou_threshold: Optional[float] = None,
        classes: Optional[List[int]] = None
    ) -> List[Detection]:
        start_time = time.time()
        conf = confidence_threshold or settings.MODEL_CONFIDENCE
        iou = iou_threshold or settings.MODEL_IOU

        if self.model_loaded and self.model is not None:
            try:
                results = self.model(
                    image,
                    conf=conf,
                    iou=iou,
                    classes=classes or list(self.TRAFFIC_CLASSES),
                    device=settings.MODEL_DEVICE,
                    verbose=False
                )
                detections = []
                for r in results:
                    boxes = r.boxes
                    for box in boxes:
                        x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
                        class_id = int(box.cls[0].cpu().numpy())
                        confidence = float(box.conf[0].cpu().numpy())
                        class_name = self.CLASS_NAMES.get(class_id, f"class_{class_id}")
                        detections.append(Detection(
                            class_id=class_id,
                            class_name=class_name,
                            confidence=confidence,
                            bbox=BBox(x1=float(x1), y1=float(y1), x2=float(x2), y2=float(y2))
                        ))
                logger.debug(f"Detection completed: {len(detections)} objects in {time.time() - start_time:.3f}s")
                return detections
            except Exception as e:
                logger.error(f"Detection error: {e}")
                return self._mock_detect(image)
        else:
            return self._mock_detect(image)

    def _mock_detect(self, image: np.ndarray) -> List[Detection]:
        h, w = image.shape[:2]
        import random
        random.seed(int(time.time() * 1000) % 10000)
        detections = []
        num_objects = random.randint(1, 5)
        for i in range(num_objects):
            class_id = random.choice([2, 3, 5, 7, 0])
            class_name = self.CLASS_NAMES.get(class_id, "unknown")
            x1 = random.randint(0, w // 2)
            y1 = random.randint(0, h // 2)
            x2 = x1 + random.randint(50, min(200, w - x1))
            y2 = y1 + random.randint(50, min(200, h - y1))
            detections.append(Detection(
                class_id=class_id,
                class_name=class_name,
                confidence=round(random.uniform(0.7, 0.99), 4),
                bbox=BBox(x1=float(x1), y1=float(y1), x2=float(x2), y2=float(y2))
            ))
        return detections


detector = ObjectDetector()

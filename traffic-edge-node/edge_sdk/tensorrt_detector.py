import logging
import os
import time
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)


class TensorRTDetector:
    EVENT_TYPE_MAP = {
        "accident": "ACCIDENT",
        "debris": "DEBRIS",
        "reverse": "REVERSE",
        "parking": "PARKING",
        "pedestrian": "PEDESTRIAN",
        "congestion": "CONGESTION",
        "stopped_vehicle": "STOPPED_VEHICLE",
        "intrusion": "INTRUSION",
        "fire": "FIRE",
    }

    def __init__(
        self,
        model_path: Optional[str] = None,
        engine_path: Optional[str] = None,
        labels: Optional[List[str]] = None,
        confidence_threshold: float = 0.5,
        nms_threshold: float = 0.45,
        input_shape: tuple = (1, 3, 640, 640),
        cooldown_seconds: float = 5.0,
    ):
        self.model_path = model_path
        self.engine_path = engine_path
        self.labels = labels or [
            "accident", "debris", "reverse", "parking",
            "pedestrian", "congestion", "stopped_vehicle",
        ]
        self.confidence_threshold = confidence_threshold
        self.nms_threshold = nms_threshold
        self.input_shape = input_shape
        self.cooldown_seconds = cooldown_seconds
        self._engine = None
        self._context = None
        self._last_event_time: Dict[str, float] = {}
        self._loaded = False

    def load_engine(self) -> bool:
        if self._loaded:
            return True
        try:
            import tensorrt as trt
            logger.info(f"Loading TensorRT engine: {self.engine_path}")
            trt_logger = trt.Logger(trt.Logger.WARNING)
            runtime = trt.Runtime(trt_logger)
            if self.engine_path and os.path.exists(self.engine_path):
                with open(self.engine_path, "rb") as f:
                    engine_data = f.read()
                self._engine = runtime.deserialize_cuda_engine(engine_data)
                if self._engine:
                    self._context = self._engine.create_execution_context()
                    self._loaded = True
                    logger.info("TensorRT engine loaded successfully")
                    return True
            logger.warning("TensorRT engine file not found, falling back to ONNX mode")
            return self._load_onnx()
        except ImportError:
            logger.warning("tensorrt not installed, trying ONNX runtime")
            return self._load_onnx()
        except Exception as e:
            logger.error(f"Failed to load TensorRT engine: {e}")
            return self._load_onnx()

    def _load_onnx(self) -> bool:
        try:
            import onnxruntime as ort
            onnx_path = self.model_path or self.engine_path
            if onnx_path and os.path.exists(onnx_path):
                providers = ["CUDAExecutionProvider", "CPUExecutionProvider"]
                self._engine = ort.InferenceSession(onnx_path, providers=providers)
                self._loaded = True
                logger.info(f"ONNX model loaded: {onnx_path}")
                return True
            logger.warning("No ONNX model file found, running in mock mode")
            self._loaded = True
            return True
        except ImportError:
            logger.warning("onnxruntime not installed, running in mock mode")
            self._loaded = True
            return True
        except Exception as e:
            logger.error(f"Failed to load ONNX model: {e}")
            self._loaded = True
            return True

    def _preprocess(self, frame) -> Any:
        try:
            import cv2
            import numpy as np
            img = cv2.resize(frame, (self.input_shape[2], self.input_shape[3]))
            img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
            img = img.astype(np.float32) / 255.0
            img = img.transpose(2, 0, 1)
            img = np.expand_dims(img, axis=0)
            return img
        except ImportError:
            return None

    def _infer_tensorrt(self, input_data) -> Optional[Any]:
        try:
            import numpy as np
            import tensorrt as trt
            import pycuda.driver as cuda
            import pycuda.autoinit

            outputs = []
            bindings = []
            stream = cuda.Stream()

            input_name = self._engine[0]
            output_name = self._engine[1]
            input_size = input_data.nbytes
            output_shape = self._engine.get_binding_shape(output_name)
            output_size = trt.volume(output_shape) * np.dtype(np.float32).itemsize

            d_input = cuda.mem_alloc(input_size)
            d_output = cuda.mem_alloc(output_size)
            bindings.append(int(d_input))
            bindings.append(int(d_output))

            cuda.memcpy_htod_async(d_input, input_data, stream)
            self._context.execute_async_v2(bindings=bindings, stream_handle=stream.handle)
            output = np.empty(output_shape, dtype=np.float32)
            cuda.memcpy_dtoh_async(d_output, output, stream)
            stream.synchronize()
            return output
        except Exception as e:
            logger.error(f"TensorRT inference error: {e}")
            return None

    def _infer_onnx(self, input_data) -> Optional[Any]:
        try:
            if hasattr(self._engine, "run"):
                input_name = self._engine.get_inputs()[0].name
                outputs = self._engine.run(None, {input_name: input_data})
                return outputs[0]
        except Exception as e:
            logger.error(f"ONNX inference error: {e}")
        return None

    def _infer(self, input_data) -> Optional[Any]:
        if input_data is None:
            return None
        try:
            import tensorrt as trt
            if self._context is not None:
                return self._infer_tensorrt(input_data)
        except ImportError:
            pass
        return self._infer_onnx(input_data)

    def _postprocess(self, output, original_shape) -> List[Dict[str, Any]]:
        if output is None:
            return []
        try:
            import numpy as np
            detections = []
            for det in output:
                if len(det) >= 6:
                    x1, y1, x2, y2, conf, cls_id = det[:6]
                    if conf < self.confidence_threshold:
                        continue
                    cls_id = int(cls_id)
                    label = self.labels[cls_id] if cls_id < len(self.labels) else f"class_{cls_id}"
                    detections.append({
                        "bbox": [float(x1), float(y1), float(x2), float(y2)],
                        "confidence": float(conf),
                        "class_id": cls_id,
                        "label": label,
                    })
            return detections
        except Exception as e:
            logger.debug(f"Post-process error: {e}")
            return []

    def _mock_detect(self, frame) -> List[Dict[str, Any]]:
        import random
        if random.random() > 0.02:
            return []
        label = random.choice(self.labels)
        h, w = frame.shape[:2]
        return [{
            "bbox": [
                float(random.randint(0, w // 2)),
                float(random.randint(0, h // 2)),
                float(random.randint(w // 2, w)),
                float(random.randint(h // 2, h)),
            ],
            "confidence": round(random.uniform(0.5, 0.98), 4),
            "class_id": self.labels.index(label) if label in self.labels else 0,
            "label": label,
        }]

    def detect(self, frame) -> List[Dict[str, Any]]:
        if frame is None:
            return []

        has_real_engine = self._context is not None or (
            hasattr(self._engine, "run")
        )
        if not has_real_engine:
            return self._mock_detect(frame)

        input_data = self._preprocess(frame)
        if input_data is None:
            return []
        output = self._infer(input_data)
        detections = self._postprocess(output, frame.shape)
        return detections

    def check_cooldown(self, event_type: str) -> bool:
        last = self._last_event_time.get(event_type, 0)
        return (time.time() - last) >= self.cooldown_seconds

    def mark_event(self, event_type: str):
        self._last_event_time[event_type] = time.time()

    def build_event_data(
        self,
        detection: Dict[str, Any],
        camera_id: int,
        camera_name: str,
        frame_ts: float,
    ) -> Dict[str, Any]:
        label = detection.get("label", "unknown")
        event_type = self.EVENT_TYPE_MAP.get(label, label.upper())
        confidence = detection.get("confidence", 0.0)
        bbox = detection.get("bbox", [])
        return {
            "eventType": event_type,
            "eventLevel": 3 if event_type in ("ACCIDENT", "REVERSE") else 2,
            "cameraId": camera_id,
            "cameraName": camera_name,
            "confidence": round(confidence, 4),
            "bbox": bbox,
            "eventTime": time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime(frame_ts)),
            "description": f"边缘AI检测到{label}事件",
        }

    def is_loaded(self) -> bool:
        return self._loaded

    def release(self):
        self._engine = None
        self._context = None
        self._loaded = False
        logger.info("TensorRTDetector released")

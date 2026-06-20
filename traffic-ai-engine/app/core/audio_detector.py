import json
import logging
import math
import time
import threading
import uuid
from collections import deque
from datetime import datetime
from typing import Deque, Dict, List, Optional, Tuple

import numpy as np

from app.config.settings import settings

logger = logging.getLogger(__name__)


class AudioEventType:
    HORN = "HORN"
    COLLISION = "COLLISION"
    SIREN = "SIREN"
    ABNORMAL_NOISE = "ABNORMAL_NOISE"


class AudioEvent:
    def __init__(
        self,
        event_type: str,
        confidence: float,
        duration: float,
        peak_db: float,
        avg_db: float,
        dominant_freq: float,
        timestamp: float,
        camera_id: Optional[str] = None,
        metadata: Optional[Dict] = None,
    ):
        self.event_type = event_type
        self.confidence = confidence
        self.duration = duration
        self.peak_db = peak_db
        self.avg_db = avg_db
        self.dominant_freq = dominant_freq
        self.timestamp = timestamp
        self.camera_id = camera_id
        self.metadata = metadata or {}

    def to_dict(self) -> Dict:
        return {
            "eventType": self.event_type,
            "confidence": round(self.confidence, 4),
            "duration": round(self.duration, 2),
            "peakDb": round(self.peak_db, 2),
            "avgDb": round(self.avg_db, 2),
            "dominantFreq": round(self.dominant_freq, 2),
            "timestamp": datetime.fromtimestamp(self.timestamp).isoformat(),
            "cameraId": self.camera_id,
            "metadata": self.metadata,
        }


class AudioDetector:
    def __init__(self):
        self.sample_rate = settings.AUDIO_SAMPLE_RATE
        self.chunk_size = settings.AUDIO_CHUNK_SIZE
        self.channels = settings.AUDIO_CHANNELS
        self.mic_array_strategy = settings.AUDIO_MIC_ARRAY_STRATEGY

        self._active_streams: Dict[str, Dict] = {}
        self._lock = threading.Lock()
        self._event_callbacks: List = []

        self.horn_freq_range = (400, 3500)
        self.horn_min_duration = settings.AUDIO_HORN_MIN_DURATION
        self.horn_max_duration = 30.0
        self.horn_min_db = settings.AUDIO_HORN_MIN_DB
        self.horn_db_above_ambient = settings.AUDIO_HORN_DB_ABOVE_AMBIENT
        self.horn_band_ratio = settings.AUDIO_HORN_BAND_RATIO

        self.collision_freq_range = (100, 800)
        self.collision_impulse_duration_max = settings.AUDIO_COLLISION_IMPULSE_MAX_RISE
        self.collision_min_db = settings.AUDIO_COLLISION_MIN_DB
        self.collision_db_above_ambient = settings.AUDIO_COLLISION_DB_ABOVE_AMBIENT
        self.collision_rise_fall_ratio = settings.AUDIO_COLLISION_RISE_FALL_RATIO

        self.siren_freq_range = (500, 2500)
        self.siren_min_duration = settings.AUDIO_SIREN_MIN_DURATION
        self.siren_db_above_ambient = settings.AUDIO_SIREN_DB_ABOVE_AMBIENT
        self.siren_band_ratio = settings.AUDIO_SIREN_BAND_RATIO

        self.ambient_db = 50.0
        self.ambient_update_alpha = settings.AUDIO_AMBIENT_UPDATE_ALPHA

        self._cooldown_events: Dict[str, float] = {}
        self._cooldown_seconds = settings.AUDIO_EVENT_COOLDOWN

    def add_event_callback(self, callback):
        self._event_callbacks.append(callback)

    def remove_event_callback(self, callback):
        try:
            self._event_callbacks.remove(callback)
        except ValueError:
            pass

    def start_audio_stream(
        self,
        camera_id: str,
        device_index: Optional[int] = None,
        stream_url: Optional[str] = None,
    ) -> Dict:
        with self._lock:
            if camera_id in self._active_streams:
                return {"success": False, "message": f"Audio stream for camera {camera_id} already active"}

            stream_info = {
                "camera_id": camera_id,
                "device_index": device_index,
                "stream_url": stream_url,
                "running": True,
                "thread": None,
                "audio_buffer": deque(maxlen=self.sample_rate * 10),
                "raw_multi_channel_buffer": deque(maxlen=self.sample_rate * 10),
                "db_buffer": deque(maxlen=600),
                "freq_history": deque(maxlen=100),
                "last_event_time": {},
                "start_time": time.time(),
                "frame_count": 0,
                "ambient_db": self.ambient_db,
                "pending_events": deque(maxlen=50),
            }

            thread = threading.Thread(
                target=self._audio_stream_loop,
                args=(stream_info,),
                daemon=True,
            )
            stream_info["thread"] = thread
            thread.start()

            self._active_streams[camera_id] = stream_info
            logger.info(
                f"Started audio stream: camera={camera_id}, device={device_index}, "
                f"channels={self.channels}, strategy={self.mic_array_strategy}"
            )
            return {"success": True, "message": f"Audio stream started for camera {camera_id}"}

    def stop_audio_stream(self, camera_id: str) -> Dict:
        with self._lock:
            if camera_id not in self._active_streams:
                return {"success": False, "message": f"Audio stream for camera {camera_id} not found"}

            self._active_streams[camera_id]["running"] = False
            stream_info = self._active_streams.pop(camera_id)

            if stream_info["thread"]:
                stream_info["thread"].join(timeout=5)

            logger.info(f"Stopped audio stream: camera={camera_id}")
            return {"success": True, "message": f"Audio stream stopped for camera {camera_id}"}

    def get_pending_events(self, camera_id: str, max_age_seconds: float = 5.0) -> List:
        with self._lock:
            if camera_id not in self._active_streams:
                return []
            stream_info = self._active_streams[camera_id]

        now = time.time()
        events = []
        while stream_info["pending_events"]:
            evt = stream_info["pending_events"][0]
            if now - evt.timestamp <= max_age_seconds:
                break
            stream_info["pending_events"].popleft()
        return list(stream_info["pending_events"])

    def clear_pending_events(self, camera_id: str):
        with self._lock:
            if camera_id in self._active_streams:
                self._active_streams[camera_id]["pending_events"].clear()

    def get_stream_status(self, camera_id: Optional[str] = None) -> Dict:
        with self._lock:
            if camera_id is not None:
                if camera_id in self._active_streams:
                    info = self._active_streams[camera_id]
                    return {
                        "cameraId": camera_id,
                        "running": info["running"],
                        "startTime": datetime.fromtimestamp(info["start_time"]).isoformat(),
                        "frameCount": info["frame_count"],
                        "ambientDb": round(info["ambient_db"], 2),
                        "channels": self.channels,
                        "micStrategy": self.mic_array_strategy,
                        "pendingEvents": len(info["pending_events"]),
                    }
                return {"cameraId": camera_id, "running": False}

            return {
                "activeCount": len(self._active_streams),
                "streams": [
                    {
                        "cameraId": cid,
                        "running": info["running"],
                        "frameCount": info["frame_count"],
                        "ambientDb": round(info["ambient_db"], 2),
                        "channels": self.channels,
                        "micStrategy": self.mic_array_strategy,
                    }
                    for cid, info in self._active_streams.items()
                ],
            }

    def _mix_multi_channel(self, multi_channel_data: np.ndarray) -> np.ndarray:
        if multi_channel_data.ndim == 1:
            return multi_channel_data

        num_ch = multi_channel_data.shape[1] if multi_channel_data.ndim > 1 else 1
        if num_ch == 1:
            return multi_channel_data[:, 0] if multi_channel_data.ndim > 1 else multi_channel_data

        strategy = self.mic_array_strategy.lower()

        if strategy == "average":
            return np.mean(multi_channel_data, axis=1).astype(np.float32)

        elif strategy == "delay_and_sum":
            center_ch = num_ch // 2
            mixed = np.zeros(len(multi_channel_data), dtype=np.float32)
            for ch in range(num_ch):
                delay_samples = abs(ch - center_ch) * 2
                if delay_samples == 0:
                    mixed += multi_channel_data[:, ch].astype(np.float32)
                elif ch < center_ch:
                    mixed[delay_samples:] += multi_channel_data[:-delay_samples, ch].astype(np.float32)
                else:
                    mixed[:-delay_samples] += multi_channel_data[delay_samples:, ch].astype(np.float32)
            return mixed / num_ch

        else:
            rms_per_ch = np.sqrt(np.mean(multi_channel_data.astype(np.float64) ** 2, axis=0))
            best_ch = int(np.argmax(rms_per_ch))
            return multi_channel_data[:, best_ch].astype(np.float32)

    def process_audio_chunk(
        self,
        camera_id: str,
        audio_data: np.ndarray,
        sample_rate: Optional[int] = None,
    ) -> List[AudioEvent]:
        sr = sample_rate or self.sample_rate
        events: List[AudioEvent] = []

        if len(audio_data) == 0:
            return events

        mono_audio = self._mix_multi_channel(audio_data)

        rms = np.sqrt(np.mean(mono_audio.astype(np.float64) ** 2))
        db = 20 * math.log10(rms + 1e-10) if rms > 0 else 0

        window_size = min(len(mono_audio), sr)
        if window_size < 64:
            return events

        windowed = mono_audio[:window_size].astype(np.float64)
        windowed = windowed * np.hanning(window_size)

        fft_data = np.abs(np.fft.rfft(windowed))
        freqs = np.fft.rfftfreq(window_size, 1.0 / sr)

        if len(fft_data) == 0:
            return events

        dominant_freq_idx = np.argmax(fft_data[1:]) + 1
        dominant_freq = freqs[dominant_freq_idx] if dominant_freq_idx < len(freqs) else 0

        with self._lock:
            if camera_id not in self._active_streams:
                return events
            stream_info = self._active_streams[camera_id]
            stream_info["db_buffer"].append(db)
            stream_info["freq_history"].append({
                "freq": dominant_freq,
                "db": db,
                "time": time.time(),
            })

            alpha = self.ambient_update_alpha
            stream_info["ambient_db"] = stream_info["ambient_db"] * (1 - alpha) + db * alpha

        horn_events = self._detect_horn(camera_id, db, dominant_freq, freqs, fft_data, sr)
        for evt in horn_events:
            events.append(evt)
            stream_info["pending_events"].append(evt)

        collision_events = self._detect_collision(camera_id, mono_audio, db, dominant_freq, freqs, fft_data, sr)
        for evt in collision_events:
            events.append(evt)
            stream_info["pending_events"].append(evt)

        siren_events = self._detect_siren(camera_id, db, dominant_freq, freqs, fft_data, sr)
        for evt in siren_events:
            events.append(evt)
            stream_info["pending_events"].append(evt)

        return events

    def _detect_horn(
        self,
        camera_id: str,
        db: float,
        dominant_freq: float,
        freqs: np.ndarray,
        fft_data: np.ndarray,
        sample_rate: int,
    ) -> List[AudioEvent]:
        events: List[AudioEvent] = []

        with self._lock:
            if camera_id not in self._active_streams:
                return events
            stream_info = self._active_streams[camera_id]

        freq_min, freq_max = self.horn_freq_range
        ambient = stream_info["ambient_db"]

        if not (freq_min <= dominant_freq <= freq_max):
            return events

        if db < max(self.horn_min_db, ambient + self.horn_db_above_ambient):
            return events

        freq_mask = (freqs >= freq_min) & (freqs <= freq_max)
        band_energy = np.sum(fft_data[freq_mask] ** 2) if np.any(freq_mask) else 0
        total_energy = np.sum(fft_data[1:] ** 2) if len(fft_data) > 1 else 1
        band_ratio = band_energy / total_energy if total_energy > 0 else 0

        if band_ratio < self.horn_band_ratio:
            return events

        now = time.time()
        horn_key = f"horn_{camera_id}"

        if horn_key not in stream_info["last_event_time"]:
            stream_info["last_event_time"][horn_key] = {
                "start_time": now,
                "last_seen": now,
                "peak_db": db,
                "dominant_freq": dominant_freq,
                "active": True,
            }
        else:
            horn_info = stream_info["last_event_time"][horn_key]
            horn_info["last_seen"] = now
            horn_info["peak_db"] = max(horn_info["peak_db"], db)
            horn_info["dominant_freq"] = dominant_freq

        return events

    def _check_horn_completion(self, camera_id: str) -> List[AudioEvent]:
        events: List[AudioEvent] = []
        now = time.time()
        horn_key = f"horn_{camera_id}"

        with self._lock:
            if camera_id not in self._active_streams:
                return events
            stream_info = self._active_streams[camera_id]

        if horn_key not in stream_info["last_event_time"]:
            return events

        horn_info = stream_info["last_event_time"][horn_key]
        if now - horn_info["last_seen"] < 0.5:
            return events

        duration = horn_info["last_seen"] - horn_info["start_time"]

        if duration >= self.horn_min_duration:
            event_key = f"horn_{camera_id}"
            if not self._is_in_cooldown(event_key):
                confidence = min(0.95, 0.6 + min(duration / 10, 0.35))
                event = AudioEvent(
                    event_type=AudioEventType.HORN,
                    confidence=confidence,
                    duration=duration,
                    peak_db=horn_info["peak_db"],
                    avg_db=stream_info["ambient_db"] + self.horn_db_above_ambient,
                    dominant_freq=horn_info["dominant_freq"],
                    timestamp=horn_info["start_time"],
                    camera_id=camera_id,
                    metadata={
                        "horn_duration": round(duration, 2),
                        "peak_db": round(horn_info["peak_db"], 2),
                        "dominant_freq_hz": round(horn_info["dominant_freq"], 2),
                        "ambient_db": round(stream_info["ambient_db"], 2),
                        "db_above_ambient": round(horn_info["peak_db"] - stream_info["ambient_db"], 2),
                    },
                )
                events.append(event)
                logger.warning(
                    f"检测到长时间鸣笛: camera={camera_id}, duration={duration:.1f}s, "
                    f"peak_db={horn_info['peak_db']:.1f}, freq={horn_info['dominant_freq']:.0f}Hz"
                )

        del stream_info["last_event_time"][horn_key]
        return events

    def _detect_collision(
        self,
        camera_id: str,
        audio_data: np.ndarray,
        db: float,
        dominant_freq: float,
        freqs: np.ndarray,
        fft_data: np.ndarray,
        sample_rate: int,
    ) -> List[AudioEvent]:
        events: List[AudioEvent] = []

        with self._lock:
            if camera_id not in self._active_streams:
                return events
            stream_info = self._active_streams[camera_id]

        ambient = stream_info["ambient_db"]

        if db < max(self.collision_min_db, ambient + self.collision_db_above_ambient):
            return events

        freq_min, freq_max = self.collision_freq_range
        if not (freq_min <= dominant_freq <= freq_max):
            return events

        if len(audio_data) < 64:
            return events

        envelope = np.abs(audio_data.astype(np.float64))
        peak_idx = np.argmax(envelope)

        rise_samples = peak_idx
        fall_samples = len(audio_data) - peak_idx
        rise_time = rise_samples / sample_rate
        fall_time = fall_samples / sample_rate

        rise_fall_ratio = rise_time / fall_time if fall_time > 0 else 0

        is_impulse = (
            rise_time < self.collision_impulse_duration_max
            and rise_fall_ratio < self.collision_rise_fall_ratio
            and db > ambient + (self.collision_db_above_ambient - 5)
        )

        if not is_impulse:
            return events

        event_key = f"collision_{camera_id}"
        if self._is_in_cooldown(event_key):
            return events

        confidence = min(
            0.95,
            0.65 + max(0.0, (db - ambient - self.collision_db_above_ambient)) / 40.0 * 0.3
        )

        event = AudioEvent(
            event_type=AudioEventType.COLLISION,
            confidence=confidence,
            duration=rise_time + fall_time,
            peak_db=db,
            avg_db=ambient,
            dominant_freq=dominant_freq,
            timestamp=time.time(),
            camera_id=camera_id,
            metadata={
                "impulse_rise_time": round(rise_time, 4),
                "impulse_fall_time": round(fall_time, 4),
                "rise_fall_ratio": round(rise_fall_ratio, 4),
                "peak_db": round(db, 2),
                "dominant_freq_hz": round(dominant_freq, 2),
                "ambient_db": round(ambient, 2),
                "db_above_ambient": round(db - ambient, 2),
            },
        )
        events.append(event)
        logger.warning(
            f"检测到碰撞声: camera={camera_id}, db={db:.1f}, freq={dominant_freq:.0f}Hz, "
            f"rise_time={rise_time:.3f}s, confidence={confidence:.3f}"
        )

        return events

    def _detect_siren(
        self,
        camera_id: str,
        db: float,
        dominant_freq: float,
        freqs: np.ndarray,
        fft_data: np.ndarray,
        sample_rate: int,
    ) -> List[AudioEvent]:
        events: List[AudioEvent] = []

        with self._lock:
            if camera_id not in self._active_streams:
                return events
            stream_info = self._active_streams[camera_id]

        ambient = stream_info["ambient_db"]
        freq_min, freq_max = self.siren_freq_range

        if not (freq_min <= dominant_freq <= freq_max):
            return events

        if db < ambient + self.siren_db_above_ambient:
            return events

        freq_mask = (freqs >= freq_min) & (freqs <= freq_max)
        if not np.any(freq_mask):
            return events

        band_energy = np.sum(fft_data[freq_mask] ** 2)
        total_energy = np.sum(fft_data[1:] ** 2) if len(fft_data) > 1 else 1
        band_ratio = band_energy / total_energy if total_energy > 0 else 0

        if band_ratio < self.siren_band_ratio:
            return events

        peaks = np.argsort(fft_data[freq_mask])[-3:]
        peak_freqs = freqs[freq_mask][peaks]
        if len(peak_freqs) >= 2:
            freq_diffs = np.diff(np.sort(peak_freqs))
            is_modulated = np.any((freq_diffs > 100) & (freq_diffs < 800))
        else:
            is_modulated = False

        if not is_modulated:
            return events

        now = time.time()
        siren_key = f"siren_{camera_id}"

        if siren_key not in stream_info["last_event_time"]:
            stream_info["last_event_time"][siren_key] = {
                "start_time": now,
                "last_seen": now,
                "peak_db": db,
                "dominant_freq": dominant_freq,
            }
        else:
            stream_info["last_event_time"][siren_key]["last_seen"] = now
            stream_info["last_event_time"][siren_key]["peak_db"] = max(
                stream_info["last_event_time"][siren_key]["peak_db"], db
            )

        return events

    def _check_siren_completion(self, camera_id: str) -> List[AudioEvent]:
        events: List[AudioEvent] = []
        now = time.time()
        siren_key = f"siren_{camera_id}"

        with self._lock:
            if camera_id not in self._active_streams:
                return events
            stream_info = self._active_streams[camera_id]

        if siren_key not in stream_info["last_event_time"]:
            return events

        siren_info = stream_info["last_event_time"][siren_key]
        if now - siren_info["last_seen"] < 1.0:
            return events

        duration = now - siren_info["start_time"]

        if duration >= self.siren_min_duration:
            event_key = f"siren_{camera_id}"
            if not self._is_in_cooldown(event_key):
                confidence = min(0.9, 0.65 + min(duration / 15, 0.25))
                event = AudioEvent(
                    event_type=AudioEventType.SIREN,
                    confidence=confidence,
                    duration=duration,
                    peak_db=siren_info["peak_db"],
                    avg_db=stream_info["ambient_db"] + self.siren_db_above_ambient,
                    dominant_freq=siren_info["dominant_freq"],
                    timestamp=siren_info["start_time"],
                    camera_id=camera_id,
                    metadata={
                        "siren_duration": round(duration, 2),
                        "peak_db": round(siren_info["peak_db"], 2),
                        "dominant_freq_hz": round(siren_info["dominant_freq"], 2),
                        "ambient_db": round(stream_info["ambient_db"], 2),
                    },
                )
                events.append(event)
                logger.warning(
                    f"检测到警笛声: camera={camera_id}, duration={duration:.1f}s, "
                    f"peak_db={siren_info['peak_db']:.1f}"
                )

        del stream_info["last_event_time"][siren_key]
        return events

    def check_completed_events(self, camera_id: str) -> List[AudioEvent]:
        events: List[AudioEvent] = []
        events.extend(self._check_horn_completion(camera_id))
        events.extend(self._check_siren_completion(camera_id))

        with self._lock:
            if camera_id in self._active_streams:
                stream_info = self._active_streams[camera_id]
                for evt in events:
                    stream_info["pending_events"].append(evt)
        return events

    def _audio_stream_loop(self, stream_info: Dict):
        camera_id = stream_info["camera_id"]
        audio = None
        stream = None

        try:
            import sounddevice as sd

            device_index = stream_info.get("device_index")
            num_channels = self.channels if self.channels >= 1 else 1

            def audio_callback(indata, frames, time_info, status):
                if status:
                    logger.debug(f"Audio stream status: {status}")
                stream_info["raw_multi_channel_buffer"].extend(indata.tolist())
                mixed = self._mix_multi_channel(indata)
                stream_info["audio_buffer"].extend(mixed.tolist())
                stream_info["frame_count"] += 1

            stream = sd.InputStream(
                device=device_index,
                channels=num_channels,
                samplerate=self.sample_rate,
                blocksize=self.chunk_size,
                dtype="float32",
                callback=audio_callback,
            )
            stream.start()
            logger.info(
                f"Audio capture started: camera={camera_id}, "
                f"device={device_index}, channels={num_channels}, sr={self.sample_rate}"
            )

        except ImportError:
            logger.warning("sounddevice not available, using simulated audio")
            stream = None
        except Exception as e:
            logger.warning(f"Failed to open audio device: {e}, using simulated mode")
            stream = None

        chunk_interval = self.chunk_size / self.sample_rate
        last_process_time = time.time()
        last_check_time = time.time()

        while stream_info["running"]:
            now = time.time()

            if now - last_process_time < chunk_interval:
                time.sleep(0.01)
                continue

            last_process_time = now

            if len(stream_info["audio_buffer"]) >= self.chunk_size:
                audio_data = np.array(list(stream_info["audio_buffer"])[:self.chunk_size])
                stream_info["audio_buffer"] = deque(
                    list(stream_info["audio_buffer"])[self.chunk_size:],
                    maxlen=self.sample_rate * 10,
                )

                audio_events = self.process_audio_chunk(camera_id, audio_data)

                for event in audio_events:
                    self._notify_event(event)
            elif stream is None:
                sim_data = self._generate_simulated_audio(stream_info)
                audio_events = self.process_audio_chunk(camera_id, sim_data)
                for event in audio_events:
                    self._notify_event(event)

            if now - last_check_time >= 1.0:
                last_check_time = now
                completed = self.check_completed_events(camera_id)
                for event in completed:
                    self._notify_event(event)

        if stream is not None:
            try:
                stream.stop()
                stream.close()
            except Exception:
                pass

        logger.info(f"Audio stream loop ended: camera={camera_id}")

    def _generate_simulated_audio(self, stream_info: Dict) -> np.ndarray:
        t = np.linspace(0, self.chunk_size / self.sample_rate, self.chunk_size)
        noise = np.random.normal(0, 0.01, self.chunk_size)
        ambient = 0.02 * np.sin(2 * np.pi * 120 * t)
        return (noise + ambient).astype(np.float32)

    def _notify_event(self, event: AudioEvent):
        for callback in self._event_callbacks:
            try:
                callback(event)
            except Exception as e:
                logger.error(f"Audio event callback error: {e}")

    def _is_in_cooldown(self, event_key: str) -> bool:
        now = time.time()
        last_time = self._cooldown_events.get(event_key, 0)
        if now - last_time < self._cooldown_seconds:
            return True
        self._cooldown_events[event_key] = now
        return False

    def get_threshold_config(self) -> Dict:
        return {
            "horn": {
                "freq_range": list(self.horn_freq_range),
                "min_duration": self.horn_min_duration,
                "min_db": self.horn_min_db,
                "db_above_ambient": self.horn_db_above_ambient,
                "band_ratio": self.horn_band_ratio,
            },
            "collision": {
                "freq_range": list(self.collision_freq_range),
                "impulse_max_rise": self.collision_impulse_duration_max,
                "min_db": self.collision_min_db,
                "db_above_ambient": self.collision_db_above_ambient,
                "rise_fall_ratio": self.collision_rise_fall_ratio,
            },
            "siren": {
                "freq_range": list(self.siren_freq_range),
                "min_duration": self.siren_min_duration,
                "db_above_ambient": self.siren_db_above_ambient,
                "band_ratio": self.siren_band_ratio,
            },
            "general": {
                "cooldown_seconds": self._cooldown_seconds,
                "ambient_update_alpha": self.ambient_update_alpha,
                "mic_array_strategy": self.mic_array_strategy,
                "channels": self.channels,
                "sample_rate": self.sample_rate,
            },
        }


audio_detector = AudioDetector()

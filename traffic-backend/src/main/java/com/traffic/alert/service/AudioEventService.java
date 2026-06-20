package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.dto.AudioEventCallbackRequest;
import com.traffic.alert.dto.AiEventCallbackRequest;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.entity.AudioEvent;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.mapper.AudioEventMapper;
import com.traffic.alert.mapper.AlertEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioEventService {

    private final AudioEventMapper audioEventMapper;
    private final AlertEventMapper alertEventMapper;
    private final CameraService cameraService;
    private final AlertEventService alertEventService;

    private static final DateTimeFormatter EVENT_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public AudioEvent handleAudioEventCallback(AudioEventCallbackRequest request) {
        AudioEvent audioEvent = new AudioEvent();

        String eventNo = request.getEventNo();
        if (eventNo == null || eventNo.isEmpty()) {
            eventNo = "AUD" + LocalDateTime.now().format(EVENT_NO_FORMATTER) +
                    String.format("%04d", (int) (Math.random() * 10000));
        }
        audioEvent.setEventNo(eventNo);
        audioEvent.setCameraId(request.getCameraId());
        audioEvent.setEventType(normalizeAudioEventType(request.getEventType()));
        audioEvent.setConfidence(request.getConfidence());
        audioEvent.setDuration(request.getDuration());
        audioEvent.setPeakDb(request.getPeakDb());
        audioEvent.setAvgDb(request.getAvgDb());
        audioEvent.setDominantFreq(request.getDominantFreq());
        audioEvent.setEventTime(request.getEventTime() != null ? request.getEventTime() : LocalDateTime.now());
        audioEvent.setDescription(request.getDescription());
        audioEvent.setAlertStatus(0);

        if (request.getCameraId() != null) {
            Camera camera = cameraService.getById(request.getCameraId());
            if (camera != null) {
                audioEvent.setCameraName(camera.getCameraName());
                audioEvent.setLongitude(camera.getLongitude());
                audioEvent.setLatitude(camera.getLatitude());
                audioEvent.setLocation(camera.getLocation());
            }
        }

        if (request.getMetadata() != null) {
            Object ambientDb = request.getMetadata().get("ambient_db");
            if (ambientDb != null) {
                try {
                    audioEvent.setAmbientDb(new java.math.BigDecimal(ambientDb.toString()));
                } catch (Exception ignored) {
                }
            }
        }

        audioEventMapper.insert(audioEvent);
        log.info("保存音频事件: eventNo={}, type={}, camera={}, duration={}s",
                eventNo, request.getEventType(), audioEvent.getCameraName(),
                request.getDuration() != null ? request.getDuration().toPlainString() : "0");

        try {
            Long linkedAlertId = linkToAlertEvent(audioEvent, request);
            if (linkedAlertId != null) {
                audioEvent.setLinkedAlertEventId(linkedAlertId);
                audioEventMapper.updateById(audioEvent);
            }
        } catch (Exception e) {
            log.warn("关联告警事件失败: eventNo={}, error={}", eventNo, e.getMessage());
        }

        return audioEvent;
    }

    private Long linkToAlertEvent(AudioEvent audioEvent, AudioEventCallbackRequest request) {
        String eventType = audioEvent.getEventType();
        Long cameraId = audioEvent.getCameraId();

        if (cameraId == null) {
            return null;
        }

        LocalDateTime since = audioEvent.getEventTime().minusMinutes(2);

        if ("HORN".equals(eventType)) {
            LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AlertEvent::getCameraId, cameraId)
                    .in(AlertEvent::getEventType, "CONGESTION", "ACCIDENT")
                    .ge(AlertEvent::getEventTime, since)
                    .orderByDesc(AlertEvent::getEventTime)
                    .last("LIMIT 1");
            AlertEvent alertEvent = alertEventMapper.selectOne(wrapper);
            if (alertEvent != null) {
                String desc = alertEvent.getDescription() != null ? alertEvent.getDescription() : "";
                alertEvent.setDescription(desc + "；伴随长时间鸣笛(" +
                        audioEvent.getDuration().toPlainString() + "秒)");
                if (alertEvent.getEventMetadata() != null) {
                    alertEvent.getEventMetadata().put("audio_horn_detected", true);
                    alertEvent.getEventMetadata().put("horn_duration", audioEvent.getDuration());
                }
                alertEventMapper.updateById(alertEvent);
                log.info("鸣笛事件关联到告警: alertId={}, audioEventNo={}", alertEvent.getId(), audioEvent.getEventNo());
                return alertEvent.getId();
            }
        } else if ("COLLISION_SOUND".equals(eventType)) {
            LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AlertEvent::getCameraId, cameraId)
                    .eq(AlertEvent::getEventType, "ACCIDENT")
                    .ge(AlertEvent::getEventTime, since)
                    .orderByDesc(AlertEvent::getEventTime)
                    .last("LIMIT 1");
            AlertEvent alertEvent = alertEventMapper.selectOne(wrapper);
            if (alertEvent != null) {
                String desc = alertEvent.getDescription() != null ? alertEvent.getDescription() : "";
                alertEvent.setDescription(desc + "；检测到碰撞声(" +
                        audioEvent.getPeakDb().toPlainString() + "dB)");
                if (alertEvent.getEventMetadata() != null) {
                    alertEvent.getEventMetadata().put("audio_collision_detected", true);
                    alertEvent.getEventMetadata().put("audio_peak_db", audioEvent.getPeakDb());
                }
                alertEventMapper.updateById(alertEvent);
                log.info("碰撞声事件关联到事故告警: alertId={}, audioEventNo={}", alertEvent.getId(), audioEvent.getEventNo());
                return alertEvent.getId();
            }
        }

        createStandaloneAlertFromAudio(audioEvent);
        return null;
    }

    private void createStandaloneAlertFromAudio(AudioEvent audioEvent) {
        try {
            AiEventCallbackRequest alertRequest = new AiEventCallbackRequest();
            alertRequest.setCameraId(audioEvent.getCameraId());
            alertRequest.setEventType(audioEvent.getEventType());
            alertRequest.setConfidence(audioEvent.getConfidence());
            alertRequest.setEventTime(audioEvent.getEventTime());
            alertRequest.setDescription(audioEvent.getDescription());

            int level = 1;
            String type = audioEvent.getEventType();
            if ("COLLISION_SOUND".equals(type)) {
                level = 3;
            } else if ("HORN".equals(type)) {
                level = 2;
            } else if ("SIREN".equals(type)) {
                level = 2;
            }
            alertRequest.setEventLevel(level);

            Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("audio_event_id", audioEvent.getId());
            metadata.put("audio_event_no", audioEvent.getEventNo());
            metadata.put("duration", audioEvent.getDuration());
            metadata.put("peak_db", audioEvent.getPeakDb());
            metadata.put("dominant_freq", audioEvent.getDominantFreq());
            alertRequest.setMetadata(metadata);

            AlertEvent alert = alertEventService.handleAiEventCallback(alertRequest);
            log.info("从音频事件创建独立告警: audioEventNo={}, alertEventNo={}",
                    audioEvent.getEventNo(), alert.getEventNo());
        } catch (Exception e) {
            log.error("从音频事件创建告警失败: audioEventNo={}, error={}", audioEvent.getEventNo(), e.getMessage());
        }
    }

    private String normalizeAudioEventType(String eventType) {
        if (eventType == null) return "UNKNOWN";
        return switch (eventType.toUpperCase()) {
            case "HORN" -> "HORN";
            case "COLLISION", "COLLISION_SOUND" -> "COLLISION_SOUND";
            case "SIREN" -> "SIREN";
            case "ABNORMAL_NOISE" -> "ABNORMAL_NOISE";
            default -> eventType.toUpperCase();
        };
    }

    public AudioEvent getById(Long id) {
        return audioEventMapper.selectById(id);
    }

    public IPage<AudioEvent> page(int current, int size, Long cameraId, String eventType,
                                   Integer alertStatus, String startTime, String endTime) {
        LambdaQueryWrapper<AudioEvent> wrapper = new LambdaQueryWrapper<>();
        if (cameraId != null) {
            wrapper.eq(AudioEvent::getCameraId, cameraId);
        }
        if (eventType != null && !eventType.isEmpty()) {
            wrapper.eq(AudioEvent::getEventType, eventType);
        }
        if (alertStatus != null) {
            wrapper.eq(AudioEvent::getAlertStatus, alertStatus);
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(AudioEvent::getEventTime, LocalDateTime.parse(startTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(AudioEvent::getEventTime, LocalDateTime.parse(endTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        wrapper.orderByDesc(AudioEvent::getEventTime);
        return audioEventMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public Map<String, Object> getStatistics() {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        Long totalCount = audioEventMapper.selectCount(new LambdaQueryWrapper<>());
        Long todayCount = audioEventMapper.selectCount(new LambdaQueryWrapper<AudioEvent>()
                .ge(AudioEvent::getEventTime, today));
        Long hornCount = audioEventMapper.selectCount(new LambdaQueryWrapper<AudioEvent>()
                .eq(AudioEvent::getEventType, "HORN"));
        Long collisionCount = audioEventMapper.selectCount(new LambdaQueryWrapper<AudioEvent>()
                .eq(AudioEvent::getEventType, "COLLISION_SOUND"));
        Long sirenCount = audioEventMapper.selectCount(new LambdaQueryWrapper<AudioEvent>()
                .eq(AudioEvent::getEventType, "SIREN"));
        Long linkedCount = audioEventMapper.selectCount(new LambdaQueryWrapper<AudioEvent>()
                .isNotNull(AudioEvent::getLinkedAlertEventId));

        return Map.of(
                "totalCount", totalCount,
                "todayCount", todayCount,
                "hornCount", hornCount,
                "collisionCount", collisionCount,
                "sirenCount", sirenCount,
                "linkedCount", linkedCount
        );
    }
}

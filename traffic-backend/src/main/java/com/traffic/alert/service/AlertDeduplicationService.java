package com.traffic.alert.service;

import com.traffic.alert.dto.AiEventCallbackRequest;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.mapper.AlertEventMapper;
import com.traffic.alert.websocket.AlertWebSocket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDeduplicationService {

    private final AlertEventService alertEventService;
    private final AlertEventMapper alertEventMapper;

    public static final int MERGE_WINDOW_SECONDS = 5;
    public static final int STORM_DETECTION_WINDOW_SECONDS = 60;
    public static final int STORM_THRESHOLD_COUNT = 10;
    public static final int STORM_SUPPRESSION_MINUTES = 5;

    private static class MergedAlertInfo {
        Long originalEventId;
        String eventNo;
        Long cameraId;
        String eventType;
        LocalDateTime firstOccurTime;
        LocalDateTime lastOccurTime;
        int duplicateCount;
        List<String> descriptions;

        MergedAlertInfo(Long originalEventId, String eventNo, Long cameraId, String eventType,
                        LocalDateTime firstOccurTime, String firstDescription) {
            this.originalEventId = originalEventId;
            this.eventNo = eventNo;
            this.cameraId = cameraId;
            this.eventType = eventType;
            this.firstOccurTime = firstOccurTime;
            this.lastOccurTime = firstOccurTime;
            this.duplicateCount = 1;
            this.descriptions = Collections.synchronizedList(new ArrayList<>());
            if (firstDescription != null && !firstDescription.isEmpty()) {
                this.descriptions.add(firstDescription);
            }
        }
    }

    private static class CameraAlertTimestamps {
        final Deque<Long> timestamps = new ConcurrentLinkedDeque<>();
    }

    private static class SuppressedCameraInfo {
        Long cameraId;
        String cameraName;
        LocalDateTime suppressedAt;
        LocalDateTime suppressedUntil;
        int totalAlertCount;
        int suppressedCount;

        SuppressedCameraInfo(Long cameraId, String cameraName, LocalDateTime suppressedAt,
                              LocalDateTime suppressedUntil, int totalAlertCount) {
            this.cameraId = cameraId;
            this.cameraName = cameraName;
            this.suppressedAt = suppressedAt;
            this.suppressedUntil = suppressedUntil;
            this.totalAlertCount = totalAlertCount;
            this.suppressedCount = 0;
        }
    }

    private final Map<String, MergedAlertInfo> activeMergedAlerts = new ConcurrentHashMap<>();
    private final Map<Long, CameraAlertTimestamps> cameraAlertTimestamps = new ConcurrentHashMap<>();
    private final Map<Long, SuppressedCameraInfo> suppressedCameras = new ConcurrentHashMap<>();

    public static class DeduplicationResult {
        private final boolean shouldProceed;
        private final boolean isMerged;
        private final boolean isSuppressed;
        private final Long originalEventId;
        private final String mergeKey;
        private final String message;
        private final AlertEvent createdEvent;

        private DeduplicationResult(boolean shouldProceed, boolean isMerged, boolean isSuppressed,
                                    Long originalEventId, String mergeKey, String message, AlertEvent createdEvent) {
            this.shouldProceed = shouldProceed;
            this.isMerged = isMerged;
            this.isSuppressed = isSuppressed;
            this.originalEventId = originalEventId;
            this.mergeKey = mergeKey;
            this.message = message;
            this.createdEvent = createdEvent;
        }

        public static DeduplicationResult proceed() {
            return new DeduplicationResult(true, false, false, null, null, null, null);
        }

        public static DeduplicationResult merged(String mergeKey, Long originalEventId, String message) {
            return new DeduplicationResult(false, true, false, originalEventId, mergeKey, message, null);
        }

        public static DeduplicationResult suppressed(String message) {
            return new DeduplicationResult(false, false, true, null, null, message, null);
        }

        public boolean shouldProceed() { return shouldProceed; }
        public boolean isMerged() { return isMerged; }
        public boolean isSuppressed() { return isSuppressed; }
        public Long getOriginalEventId() { return originalEventId; }
        public String getMergeKey() { return mergeKey; }
        public String getMessage() { return message; }
        public AlertEvent getCreatedEvent() { return createdEvent; }
    }

    public DeduplicationResult preProcess(AiEventCallbackRequest request) {
        Long cameraId = request.getCameraId();
        String eventType = request.getEventType();

        if (isCameraSuppressed(cameraId)) {
            SuppressedCameraInfo info = suppressedCameras.get(cameraId);
            info.suppressedCount++;
            log.warn("告警风暴抑制生效: cameraId={}, 已屏蔽数={}, 有效期至={}, eventType={}",
                    cameraId, info.suppressedCount, info.suppressedUntil, eventType);
            return DeduplicationResult.suppressed(
                    String.format("摄像头[%d]处于告警风暴抑制期，已屏蔽告警。有效期至%s，当前已屏蔽%d条",
                            cameraId, info.suppressedUntil, info.suppressedCount));
        }

        String mergeKey = buildMergeKey(cameraId, eventType);
        MergedAlertInfo existing = activeMergedAlerts.get(mergeKey);
        if (existing != null) {
            LocalDateTime now = LocalDateTime.now();
            long secondsDiff = java.time.Duration.between(existing.lastOccurTime, now).getSeconds();

            if (secondsDiff <= MERGE_WINDOW_SECONDS) {
                existing.duplicateCount++;
                existing.lastOccurTime = now;
                if (request.getDescription() != null && !request.getDescription().isEmpty()) {
                    synchronized (existing.descriptions) {
                        if (!existing.descriptions.contains(request.getDescription())) {
                            existing.descriptions.add(request.getDescription());
                        }
                    }
                }
                log.info("告警合并: cameraId={}, eventType={}, 重复次数={}, 原始事件ID={}",
                        cameraId, eventType, existing.duplicateCount, existing.originalEventId);
                return DeduplicationResult.merged(mergeKey, existing.originalEventId,
                        String.format("告警已合并到原始事件[%s]，当前重复%d次", existing.eventNo, existing.duplicateCount));
            } else {
                finalizeMergedAlert(existing);
                activeMergedAlerts.remove(mergeKey);
            }
        }

        recordCameraAlert(cameraId);
        if (detectStorm(cameraId)) {
            String cameraName = request.getCameraName();
            if (cameraName == null || cameraName.isEmpty()) {
                cameraName = "摄像头" + cameraId;
            }
            activateSuppression(cameraId, cameraName);
            SuppressedCameraInfo info = suppressedCameras.get(cameraId);
            log.error("检测到告警风暴! cameraId={}, cameraName={}, {}秒内{}条告警，屏蔽{}分钟",
                    cameraId, cameraName, STORM_DETECTION_WINDOW_SECONDS, STORM_THRESHOLD_COUNT, STORM_SUPPRESSION_MINUTES);
            broadcastStormAlert(cameraId, cameraName, info.suppressedUntil);
            return DeduplicationResult.suppressed(
                    String.format("检测到告警风暴，已自动屏蔽摄像头[%s]告警%d分钟", cameraName, STORM_SUPPRESSION_MINUTES));
        }

        return DeduplicationResult.proceed();
    }

    public void registerCreatedEvent(AlertEvent event) {
        Long cameraId = event.getCameraId();
        String eventType = event.getEventType();
        String mergeKey = buildMergeKey(cameraId, eventType);

        MergedAlertInfo info = new MergedAlertInfo(
                event.getId(),
                event.getEventNo(),
                cameraId,
                eventType,
                event.getEventTime(),
                event.getDescription()
        );
        activeMergedAlerts.put(mergeKey, info);
        log.debug("注册可合并告警: mergeKey={}, eventId={}, eventNo={}", mergeKey, event.getId(), event.getEventNo());
    }

    private String buildMergeKey(Long cameraId, String eventType) {
        return cameraId + "_" + (eventType != null ? eventType : "UNKNOWN");
    }

    private boolean isCameraSuppressed(Long cameraId) {
        SuppressedCameraInfo info = suppressedCameras.get(cameraId);
        if (info == null) return false;
        if (LocalDateTime.now().isAfter(info.suppressedUntil)) {
            suppressedCameras.remove(cameraId);
            log.info("告警风暴抑制自动解除: cameraId={}, 共屏蔽{}条告警", cameraId, info.suppressedCount);
            broadcastStormRecovery(cameraId, info.cameraName, info.suppressedCount);
            return false;
        }
        return true;
    }

    private void recordCameraAlert(Long cameraId) {
        CameraAlertTimestamps ts = cameraAlertTimestamps.computeIfAbsent(cameraId, k -> new CameraAlertTimestamps());
        long now = System.currentTimeMillis();
        ts.timestamps.addLast(now);
        long cutoff = now - (STORM_DETECTION_WINDOW_SECONDS * 1000L);
        while (!ts.timestamps.isEmpty() && ts.timestamps.peekFirst() < cutoff) {
            ts.timestamps.pollFirst();
        }
    }

    private boolean detectStorm(Long cameraId) {
        CameraAlertTimestamps ts = cameraAlertTimestamps.get(cameraId);
        if (ts == null) return false;
        return ts.timestamps.size() >= STORM_THRESHOLD_COUNT;
    }

    private void activateSuppression(Long cameraId, String cameraName) {
        CameraAlertTimestamps ts = cameraAlertTimestamps.get(cameraId);
        int count = ts != null ? ts.timestamps.size() : 0;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plusMinutes(STORM_SUPPRESSION_MINUTES);
        suppressedCameras.put(cameraId, new SuppressedCameraInfo(cameraId, cameraName, now, until, count));
        if (ts != null) {
            ts.timestamps.clear();
        }
    }

    public boolean releaseSuppression(Long cameraId) {
        SuppressedCameraInfo info = suppressedCameras.remove(cameraId);
        if (info != null) {
            log.info("手动解除告警风暴抑制: cameraId={}, 已屏蔽{}条告警", cameraId, info.suppressedCount);
            broadcastStormRecovery(cameraId, info.cameraName, info.suppressedCount);
            return true;
        }
        return false;
    }

    public void releaseAllSuppression() {
        int count = suppressedCameras.size();
        List<Long> ids = new ArrayList<>(suppressedCameras.keySet());
        for (Long id : ids) {
            releaseSuppression(id);
        }
        log.info("手动解除所有告警风暴抑制: 共{}个摄像头", count);
    }

    private void finalizeMergedAlert(MergedAlertInfo info) {
        if (info.duplicateCount <= 1) {
            return;
        }

        try {
            AlertEvent event = alertEventService.getById(info.originalEventId);
            if (event == null) {
                log.warn("合并告警原始事件不存在: eventId={}", info.originalEventId);
                return;
            }

            StringBuilder newDesc = new StringBuilder();
            newDesc.append(event.getDescription() != null ? event.getDescription() : "");
            newDesc.append(String.format("；[合并告警: 5秒内共%d次重复", info.duplicateCount));
            if (info.descriptions.size() > 1) {
                newDesc.append("，包含描述变体").append(info.descriptions.size()).append("种");
            }
            newDesc.append("]");
            event.setDescription(newDesc.toString());

            if (info.lastOccurTime != null && event.getEventTime() != null
                    && info.lastOccurTime.isAfter(event.getEventTime())) {
                event.setEventTime(info.lastOccurTime);
            }

            alertEventMapper.updateById(event);

            log.info("合并告警最终处理: eventId={}, eventNo={}, 重复{}次, 描述变体{}种",
                    info.originalEventId, info.eventNo, info.duplicateCount, info.descriptions.size());
        } catch (Exception e) {
            log.warn("合并告警最终处理失败: eventId={}, error={}", info.originalEventId, e.getMessage());
        }
    }

    @Scheduled(fixedRate = 3000)
    public void cleanupExpiredMergedAlerts() {
        LocalDateTime now = LocalDateTime.now();
        List<String> expiredKeys = new ArrayList<>();

        for (Map.Entry<String, MergedAlertInfo> entry : activeMergedAlerts.entrySet()) {
            MergedAlertInfo info = entry.getValue();
            long secondsSinceLast = java.time.Duration.between(info.lastOccurTime, now).getSeconds();
            if (secondsSinceLast > MERGE_WINDOW_SECONDS) {
                expiredKeys.add(entry.getKey());
            }
        }

        for (String key : expiredKeys) {
            MergedAlertInfo info = activeMergedAlerts.remove(key);
            if (info != null) {
                finalizeMergedAlert(info);
            }
        }
    }

    @Scheduled(fixedRate = 10000)
    public void cleanupExpiredSuppression() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> expired = new ArrayList<>();
        for (Map.Entry<Long, SuppressedCameraInfo> entry : suppressedCameras.entrySet()) {
            if (now.isAfter(entry.getValue().suppressedUntil)) {
                expired.add(entry.getKey());
            }
        }
        for (Long cameraId : expired) {
            SuppressedCameraInfo info = suppressedCameras.remove(cameraId);
            if (info != null) {
                log.info("告警风暴抑制自动解除(定时): cameraId={}, 共屏蔽{}条告警",
                        cameraId, info.suppressedCount);
                broadcastStormRecovery(cameraId, info.cameraName, info.suppressedCount);
            }
        }
    }

    private void broadcastStormAlert(Long cameraId, String cameraName, LocalDateTime until) {
        try {
            AlertWebSocket.sendAlertMessage(Map.of(
                    "type", "STORM_ALERT",
                    "cameraId", cameraId,
                    "cameraName", cameraName,
                    "alertType", "STORM_DETECTED",
                    "message", String.format("检测到告警风暴，摄像头[%s]已自动屏蔽%d分钟", cameraName, STORM_SUPPRESSION_MINUTES),
                    "suppressedUntil", until.toString(),
                    "threshold", STORM_THRESHOLD_COUNT,
                    "windowSeconds", STORM_DETECTION_WINDOW_SECONDS,
                    "timestamp", LocalDateTime.now().toString()
            ), true);
        } catch (Exception e) {
            log.warn("广播告警风暴通知失败: {}", e.getMessage());
        }
    }

    private void broadcastStormRecovery(Long cameraId, String cameraName, int suppressedCount) {
        try {
            AlertWebSocket.sendAlertMessage(Map.of(
                    "type", "STORM_RECOVERY",
                    "cameraId", cameraId,
                    "cameraName", cameraName,
                    "alertType", "STORM_RECOVERED",
                    "message", String.format("摄像头[%s]告警风暴抑制已解除，共屏蔽%d条告警", cameraName, suppressedCount),
                    "suppressedCount", suppressedCount,
                    "timestamp", LocalDateTime.now().toString()
            ), false);
        } catch (Exception e) {
            log.warn("广播告警风暴恢复通知失败: {}", e.getMessage());
        }
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("mergeWindowSeconds", MERGE_WINDOW_SECONDS);
        status.put("stormDetectionWindowSeconds", STORM_DETECTION_WINDOW_SECONDS);
        status.put("stormThresholdCount", STORM_THRESHOLD_COUNT);
        status.put("stormSuppressionMinutes", STORM_SUPPRESSION_MINUTES);

        status.put("activeMergeCount", activeMergedAlerts.size());
        List<Map<String, Object>> mergeList = new ArrayList<>();
        for (Map.Entry<String, MergedAlertInfo> entry : activeMergedAlerts.entrySet()) {
            MergedAlertInfo info = entry.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mergeKey", entry.getKey());
            m.put("cameraId", info.cameraId);
            m.put("eventType", info.eventType);
            m.put("eventNo", info.eventNo);
            m.put("originalEventId", info.originalEventId);
            m.put("duplicateCount", info.duplicateCount);
            m.put("descriptionVariants", info.descriptions.size());
            m.put("firstOccurTime", info.firstOccurTime.toString());
            m.put("lastOccurTime", info.lastOccurTime.toString());
            mergeList.add(m);
        }
        status.put("activeMerges", mergeList);

        status.put("suppressedCameraCount", suppressedCameras.size());
        List<Map<String, Object>> supList = new ArrayList<>();
        for (Map.Entry<Long, SuppressedCameraInfo> entry : suppressedCameras.entrySet()) {
            SuppressedCameraInfo info = entry.getValue();
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("cameraId", info.cameraId);
            s.put("cameraName", info.cameraName);
            s.put("suppressedAt", info.suppressedAt.toString());
            s.put("suppressedUntil", info.suppressedUntil.toString());
            s.put("triggerAlertCount", info.totalAlertCount);
            s.put("suppressedCount", info.suppressedCount);
            s.put("remainingSeconds",
                    Math.max(0, java.time.Duration.between(LocalDateTime.now(), info.suppressedUntil).getSeconds()));
            supList.add(s);
        }
        status.put("suppressedCameras", supList);

        status.put("monitoredCameraCount", cameraAlertTimestamps.size());
        List<Map<String, Object>> camStats = new ArrayList<>();
        for (Map.Entry<Long, CameraAlertTimestamps> entry : cameraAlertTimestamps.entrySet()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("cameraId", entry.getKey());
            c.put("alertCountInWindow", entry.getValue().timestamps.size());
            c.put("windowSeconds", STORM_DETECTION_WINDOW_SECONDS);
            camStats.add(c);
        }
        status.put("cameraAlertStats", camStats);

        return status;
    }
}

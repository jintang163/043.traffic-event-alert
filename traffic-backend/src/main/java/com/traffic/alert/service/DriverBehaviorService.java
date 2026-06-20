package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.config.DriverBehaviorConfig;
import com.traffic.alert.constant.EventType;
import com.traffic.alert.dto.*;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.entity.DriverBehaviorRecord;
import com.traffic.alert.mapper.DriverBehaviorRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverBehaviorService {

    private final DriverBehaviorRecordMapper recordMapper;
    private final DriverBehaviorConfig config;
    private final CameraService cameraService;
    private final AlertEventService alertEventService;
    private final LedSignService ledSignService;

    private final ConcurrentHashMap<Long, AbnormalCounter> abnormalCounterMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, LocalDateTime> lastAlertTimeMap = new ConcurrentHashMap<>();

    private static final DateTimeFormatter RECORD_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Transactional
    public DriverBehaviorRecord saveDetectionResult(DriverBehaviorAnalysisResult result) {
        DriverBehaviorRecord record = new DriverBehaviorRecord();
        record.setRecordNo(generateRecordNo());
        record.setCameraId(result.getCameraId());
        record.setCameraName(result.getCameraName());
        record.setDetectTime(result.getDetectionTime());
        record.setAlgorithmVersion(result.getAlgorithmVersion());

        record.setIsPhoneCall(Boolean.TRUE.equals(result.getIsPhoneCall()) ? 1 : 0);
        record.setPhoneCallConfidence(result.getPhoneCallConfidence());
        record.setPhoneCallRegion(result.getPhoneCallRegion());

        record.setIsYawning(Boolean.TRUE.equals(result.getIsYawning()) ? 1 : 0);
        record.setYawningConfidence(result.getYawningConfidence());
        record.setMouthOpenRatio(result.getMouthOpenRatio());

        record.setIsFatigued(Boolean.TRUE.equals(result.getIsFatigued()) ? 1 : 0);
        record.setFatigueConfidence(result.getFatigueConfidence());
        record.setEyeAspectRatio(result.getEyeAspectRatio());
        record.setPerclosScore(result.getPerclosScore());

        record.setIsDistracted(Boolean.TRUE.equals(result.getIsDistracted()) ? 1 : 0);
        record.setDistractionConfidence(result.getDistractionConfidence());
        record.setHeadPoseYaw(result.getHeadPoseYaw());
        record.setHeadPosePitch(result.getHeadPosePitch());

        record.setOverallScore(result.getOverallScore());
        record.setBehaviorLevel(result.getBehaviorLevel());
        record.setIsAbnormal(Boolean.TRUE.equals(result.getIsAbnormal()) ? 1 : 0);
        record.setAbnormalTypes(result.getAbnormalTypes());
        record.setDescription(result.getDescription());

        record.setIsRealFrame(Boolean.TRUE.equals(result.getIsRealFrame()) ? 1 : 0);
        record.setFrameCaptureCostMs(result.getFrameCaptureCostMs());
        record.setDetectionDurationMs(result.getDetectionDurationMs());

        if (Boolean.TRUE.equals(result.getIsAbnormal())) {
            handleAbnormal(result, record);
        } else {
            resetAbnormalCounter(result.getCameraId());
        }

        recordMapper.insert(record);

        if (result.getAlertEventId() != null) {
            record.setAlertEventId(result.getAlertEventId());
            recordMapper.updateById(record);
        }

        log.info("驾驶员行为检测记录已保存: recordNo={}, cameraId={}, abnormal={}, score={}",
                record.getRecordNo(), record.getCameraId(), record.getIsAbnormal(), record.getOverallScore());

        return record;
    }

    private void handleAbnormal(DriverBehaviorAnalysisResult result, DriverBehaviorRecord record) {
        Long cameraId = result.getCameraId();
        DriverBehaviorConfig.AlertRules rules = config.getAlertRules();

        AbnormalCounter counter = abnormalCounterMap.computeIfAbsent(cameraId, k -> new AbnormalCounter());
        counter.increment(result.getAbnormalTypes());

        boolean shouldAlert = counter.getConsecutiveCount() >= config.getThresholds().getConsecutiveAbnormalThreshold();
        boolean cooldownPassed = isCooldownPassed(cameraId);

        if (shouldAlert && cooldownPassed) {
            List<String> abnormalTypes = Arrays.asList(result.getAbnormalTypes().split(","));
            String primaryType = getPrimaryEventType(abnormalTypes);
            Integer eventLevel = getEventLevel(primaryType, rules);

            try {
                AiEventCallbackRequest request = buildAlertRequest(result, primaryType, eventLevel);
                Long alertEventId = alertEventService.handleAiEventCallback(request);
                result.setAlertEventId(alertEventId);
                result.setAlertTriggered(true);
                record.setAlertTriggered(1);
                record.setAlertEventId(alertEventId);
                lastAlertTimeMap.put(cameraId, LocalDateTime.now());

                if (Boolean.TRUE.equals(rules.getLedReminderEnabled())) {
                    boolean ledSuccess = triggerLedReminder(cameraId, result, primaryType);
                    result.setLedReminded(ledSuccess);
                    record.setLedReminded(ledSuccess ? 1 : 0);
                    record.setLedRemindResult(ledSuccess ? "LED提醒成功" : "LED提醒失败");
                }

                log.info("驾驶员行为异常告警已触发: cameraId={}, type={}, alertId={}",
                        cameraId, primaryType, alertEventId);
            } catch (Exception e) {
                log.error("驾驶员行为异常告警失败: cameraId={}, error={}", cameraId, e.getMessage());
                result.setAlertTriggered(false);
                record.setAlertTriggered(0);
            }
        }
    }

    private String getPrimaryEventType(List<String> abnormalTypes) {
        if (abnormalTypes.contains("FATIGUE")) return EventType.DRIVER_FATIGUE;
        if (abnormalTypes.contains("PHONE_CALL")) return EventType.DRIVER_PHONE_CALL;
        if (abnormalTypes.contains("YAWNING")) return EventType.DRIVER_YAWNING;
        if (abnormalTypes.contains("DISTRACTION")) return EventType.DRIVER_DISTRACTION;
        return EventType.DRIVER_DISTRACTION;
    }

    private Integer getEventLevel(String eventType, DriverBehaviorConfig.AlertRules rules) {
        return switch (eventType) {
            case EventType.DRIVER_FATIGUE -> rules.getFatigueLevel();
            case EventType.DRIVER_PHONE_CALL -> rules.getPhoneCallLevel();
            case EventType.DRIVER_YAWNING -> rules.getYawningLevel();
            case EventType.DRIVER_DISTRACTION -> rules.getDistractionLevel();
            default -> 2;
        };
    }

    private AiEventCallbackRequest buildAlertRequest(DriverBehaviorAnalysisResult result, String eventType, Integer eventLevel) {
        AiEventCallbackRequest request = new AiEventCallbackRequest();
        request.setCameraId(result.getCameraId());
        request.setEventType(eventType);
        request.setEventLevel(eventLevel);
        request.setEventTime(result.getDetectionTime());
        request.setDescription(result.getDescription());
        request.setConfidence(result.getOverallScore());
        request.setAlgorithmType("DRIVER_BEHAVIOR");
        return request;
    }

    private boolean triggerLedReminder(Long cameraId, DriverBehaviorAnalysisResult result, String primaryType) {
        try {
            String message = getLedMessage(primaryType, result);
            String color = "RED";
            if (result.getBehaviorLevel() != null && result.getBehaviorLevel() <= 2) {
                color = "YELLOW";
            }

            Camera camera = cameraService.getById(cameraId);
            if (camera != null) {
                Long roadSideCameraId = findRoadSideCamera(camera);
                if (roadSideCameraId != null) {
                    return ledSignService.displayMessage(
                            roadSideCameraId, message, color, true,
                            config.getAlertRules().getLedDisplaySeconds()
                    );
                }
            }

            return ledSignService.displayMessage(
                    cameraId, message, color, true,
                    config.getAlertRules().getLedDisplaySeconds()
            );
        } catch (Exception e) {
            log.warn("LED提醒失败: cameraId={}, error={}", cameraId, e.getMessage());
            return false;
        }
    }

    private String getLedMessage(String eventType, DriverBehaviorAnalysisResult result) {
        return switch (eventType) {
            case EventType.DRIVER_FATIGUE -> "请勿疲劳驾驶！请立即停车休息";
            case EventType.DRIVER_PHONE_CALL -> "请勿开车打电话！注意安全";
            case EventType.DRIVER_YAWNING -> "请勿疲劳驾驶！注意休息";
            case EventType.DRIVER_DISTRACTION -> "请集中注意力！安全驾驶";
            default -> "请注意安全驾驶！";
        };
    }

    private Long findRoadSideCamera(Camera inCarCamera) {
        try {
            List<Camera> cameras = cameraService.listAll();
            for (Camera cam : cameras) {
                if (cam.getRoadName() != null && cam.getRoadName().equals(inCarCamera.getRoadName())) {
                    if (cam.getCameraType() == null || cam.getCameraType() == 1) {
                        return cam.getId();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("查找路侧摄像头失败: {}", e.getMessage());
        }
        return null;
    }

    private boolean isCooldownPassed(Long cameraId) {
        LocalDateTime lastAlert = lastAlertTimeMap.get(cameraId);
        if (lastAlert == null) return true;
        long secondsSinceLastAlert = java.time.Duration.between(lastAlert, LocalDateTime.now()).getSeconds();
        return secondsSinceLastAlert >= config.getThresholds().getAlertCooldownSeconds();
    }

    private void resetAbnormalCounter(Long cameraId) {
        AbnormalCounter counter = abnormalCounterMap.get(cameraId);
        if (counter != null) {
            counter.reset();
        }
    }

    private String generateRecordNo() {
        return "DBR" + RECORD_NO_FORMATTER.format(LocalDateTime.now()) +
                String.format("%04d", new Random().nextInt(10000));
    }

    public PageResult<DriverBehaviorRecord> page(DriverBehaviorRecordQuery query) {
        Page<DriverBehaviorRecord> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<DriverBehaviorRecord> wrapper = new LambdaQueryWrapper<>();

        if (query.getCameraId() != null) {
            wrapper.eq(DriverBehaviorRecord::getCameraId, query.getCameraId());
        }
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(DriverBehaviorRecord::getCameraName, query.getKeyword())
                    .or().like(DriverBehaviorRecord::getDescription, query.getKeyword());
        }
        if (query.getIsAbnormal() != null) {
            wrapper.eq(DriverBehaviorRecord::getIsAbnormal, query.getIsAbnormal());
        }
        if (query.getBehaviorLevel() != null) {
            wrapper.eq(DriverBehaviorRecord::getBehaviorLevel, query.getBehaviorLevel());
        }
        if (query.getAbnormalType() != null && !query.getAbnormalType().isEmpty()) {
            wrapper.like(DriverBehaviorRecord::getAbnormalTypes, query.getAbnormalType());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(DriverBehaviorRecord::getDetectTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(DriverBehaviorRecord::getDetectTime, query.getEndTime());
        }
        if (query.getAlertTriggered() != null) {
            wrapper.eq(DriverBehaviorRecord::getAlertTriggered, query.getAlertTriggered());
        }
        if (query.getLedReminded() != null) {
            wrapper.eq(DriverBehaviorRecord::getLedReminded, query.getLedReminded());
        }
        if (query.getIsRealFrame() != null) {
            wrapper.eq(DriverBehaviorRecord::getIsRealFrame, query.getIsRealFrame());
        }

        wrapper.orderByDesc(DriverBehaviorRecord::getDetectTime);
        recordMapper.selectPage(page, wrapper);

        return PageResult.of(page);
    }

    public DriverBehaviorRecord getById(Long id) {
        return recordMapper.selectById(id);
    }

    public List<DriverBehaviorRecord> getRecentRecords(Long cameraId, int limit) {
        LambdaQueryWrapper<DriverBehaviorRecord> wrapper = new LambdaQueryWrapper<>();
        if (cameraId != null) {
            wrapper.eq(DriverBehaviorRecord::getCameraId, cameraId);
        }
        wrapper.orderByDesc(DriverBehaviorRecord::getDetectTime);
        wrapper.last("LIMIT " + limit);
        return recordMapper.selectList(wrapper);
    }

    public DriverBehaviorDashboard getDashboard() {
        DriverBehaviorDashboard dashboard = new DriverBehaviorDashboard();
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        List<Camera> allCameras = cameraService.listAll();
        List<Camera> inCarCameras = allCameras.stream()
                .filter(c -> c.getCameraType() != null && c.getCameraType() == 2)
                .toList();

        dashboard.setTotalInCarCameras(inCarCameras.size());
        dashboard.setMonitoredCount((int) inCarCameras.stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .count());

        LambdaQueryWrapper<DriverBehaviorRecord> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(DriverBehaviorRecord::getDetectTime, startOfDay);
        todayWrapper.le(DriverBehaviorRecord::getDetectTime, endOfDay);
        List<DriverBehaviorRecord> todayRecords = recordMapper.selectList(todayWrapper);

        dashboard.setTodayDetectionCount(todayRecords.size());
        dashboard.setTodayAbnormalCount((int) todayRecords.stream()
                .filter(r -> r.getIsAbnormal() != null && r.getIsAbnormal() == 1)
                .count());

        BigDecimal avgScore = todayRecords.stream()
                .map(DriverBehaviorRecord::getOverallScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!todayRecords.isEmpty()) {
            avgScore = avgScore.divide(BigDecimal.valueOf(todayRecords.size()), 2, java.math.RoundingMode.HALF_UP);
        }
        dashboard.setAvgBehaviorScore(avgScore);

        Map<String, Long> abnormalStats = new HashMap<>();
        for (DriverBehaviorRecord record : todayRecords) {
            if (record.getIsAbnormal() != null && record.getIsAbnormal() == 1
                    && record.getAbnormalTypes() != null) {
                for (String type : record.getAbnormalTypes().split(",")) {
                    abnormalStats.merge(type, 1L, Long::sum);
                }
            }
        }
        dashboard.setAbnormalTypeStats(abnormalStats);
        dashboard.setPhoneCallCount(abnormalStats.getOrDefault("PHONE_CALL", 0L));
        dashboard.setYawningCount(abnormalStats.getOrDefault("YAWNING", 0L));
        dashboard.setFatigueCount(abnormalStats.getOrDefault("FATIGUE", 0L));
        dashboard.setDistractionCount(abnormalStats.getOrDefault("DISTRACTION", 0L));

        List<DriverBehaviorAbnormalRecord> recentAbnormal = todayRecords.stream()
                .filter(r -> r.getIsAbnormal() != null && r.getIsAbnormal() == 1)
                .sorted(Comparator.comparing(DriverBehaviorRecord::getDetectTime).reversed())
                .limit(10)
                .map(this::toAbnormalRecord)
                .collect(Collectors.toList());
        dashboard.setRecentAbnormalRecords(recentAbnormal);

        List<DriverBehaviorCameraItem> cameraItems = inCarCameras.stream()
                .map(this::toCameraItem)
                .sorted(Comparator.comparing(DriverBehaviorCameraItem::getAvgBehaviorScore, Comparator.nullsFirst(BigDecimal::compareTo)))
                .collect(Collectors.toList());
        dashboard.setCameraBehaviorList(cameraItems);

        dashboard.setReportDate(today.toString());

        return dashboard;
    }

    private DriverBehaviorAbnormalRecord toAbnormalRecord(DriverBehaviorRecord record) {
        DriverBehaviorAbnormalRecord dto = new DriverBehaviorAbnormalRecord();
        dto.setId(record.getId());
        dto.setRecordNo(record.getRecordNo());
        dto.setCameraId(record.getCameraId());
        dto.setCameraName(record.getCameraName());
        dto.setDetectTime(record.getDetectTime());
        dto.setOverallScore(record.getOverallScore());
        dto.setBehaviorLevel(record.getBehaviorLevel());

        String firstType = null;
        if (record.getAbnormalTypes() != null && !record.getAbnormalTypes().isEmpty()) {
            firstType = record.getAbnormalTypes().split(",")[0];
            dto.setAbnormalType(firstType);
            dto.setAbnormalTypeName(getAbnormalTypeName(firstType));
        }
        dto.setDescription(record.getDescription());
        return dto;
    }

    private DriverBehaviorCameraItem toCameraItem(Camera camera) {
        DriverBehaviorCameraItem item = new DriverBehaviorCameraItem();
        item.setCameraId(camera.getId());
        item.setCameraName(camera.getCameraName());
        item.setCameraCode(camera.getCameraCode());
        item.setRoadName(camera.getRoadName());

        LambdaQueryWrapper<DriverBehaviorRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DriverBehaviorRecord::getCameraId, camera.getId());
        wrapper.orderByDesc(DriverBehaviorRecord::getDetectTime);
        wrapper.last("LIMIT 100");
        List<DriverBehaviorRecord> recentRecords = recordMapper.selectList(wrapper);

        if (!recentRecords.isEmpty()) {
            BigDecimal avgScore = recentRecords.stream()
                    .map(DriverBehaviorRecord::getOverallScore)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(recentRecords.size()), 2, java.math.RoundingMode.HALF_UP);
            item.setAvgBehaviorScore(avgScore);

            int level = classifyLevel(avgScore);
            item.setBehaviorLevel(level);

            long abnormalCount = recentRecords.stream()
                    .filter(r -> r.getIsAbnormal() != null && r.getIsAbnormal() == 1)
                    .count();
            item.setAbnormalCount((int) abnormalCount);

            long alertCount = recentRecords.stream()
                    .filter(r -> r.getAlertTriggered() != null && r.getAlertTriggered() == 1)
                    .count();
            item.setAlertCount((int) alertCount);

            recentRecords.stream()
                    .filter(r -> r.getIsAbnormal() != null && r.getIsAbnormal() == 1)
                    .max(Comparator.comparing(DriverBehaviorRecord::getDetectTime))
                    .ifPresent(r -> {
                        if (r.getAbnormalTypes() != null && !r.getAbnormalTypes().isEmpty()) {
                            String firstType = r.getAbnormalTypes().split(",")[0];
                            item.setLastAbnormalType(firstType);
                            item.setLastAbnormalTypeName(getAbnormalTypeName(firstType));
                        }
                    });

            recentRecords.stream()
                    .max(Comparator.comparing(DriverBehaviorRecord::getDetectTime))
                    .ifPresent(r -> item.setLastDetectTime(r.getDetectTime()));
        } else {
            item.setAvgBehaviorScore(BigDecimal.valueOf(100));
            item.setBehaviorLevel(1);
            item.setAbnormalCount(0);
            item.setAlertCount(0);
        }

        return item;
    }

    private int classifyLevel(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) return 1;
        if (score.compareTo(BigDecimal.valueOf(75)) >= 0) return 2;
        if (score.compareTo(BigDecimal.valueOf(60)) >= 0) return 3;
        if (score.compareTo(BigDecimal.valueOf(40)) >= 0) return 4;
        return 5;
    }

    private String getAbnormalTypeName(String type) {
        return switch (type) {
            case "PHONE_CALL" -> "打电话";
            case "YAWNING" -> "打哈欠";
            case "FATIGUE" -> "疲劳驾驶";
            case "DISTRACTION" -> "分心驾驶";
            default -> type;
        };
    }

    public List<Camera> listInCarCameras() {
        return cameraService.listAll().stream()
                .filter(c -> c.getCameraType() != null && c.getCameraType() == 2)
                .collect(Collectors.toList());
    }

    private static class AbnormalCounter {
        private int consecutiveCount = 0;
        private final Map<String, Integer> typeCount = new HashMap<>();

        void increment(String types) {
            consecutiveCount++;
            for (String type : types.split(",")) {
                typeCount.merge(type, 1, Integer::sum);
            }
        }

        void reset() {
            consecutiveCount = 0;
            typeCount.clear();
        }

        int getConsecutiveCount() {
            return consecutiveCount;
        }
    }
}

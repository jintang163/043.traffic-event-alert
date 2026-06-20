package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.config.VideoQualityConfig;
import com.traffic.alert.constant.EventType;
import com.traffic.alert.dto.*;
import com.traffic.alert.entity.*;
import com.traffic.alert.mapper.VideoHealthDiagnosisMapper;
import com.traffic.alert.mapper.VideoQualityRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoQualityService {

    private final VideoQualityRecordMapper recordMapper;
    private final VideoHealthDiagnosisMapper diagnosisMapper;
    private final VideoQualityAnalyzer analyzer;
    private final VideoQualityConfig config;
    private final CameraService cameraService;
    private final AlertEventService alertEventService;
    private final AiEventCallbackRequest aiEventCallbackRequestTemplate = new AiEventCallbackRequest();

    private final ConcurrentHashMap<Long, AbnormalCounter> abnormalCounterMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> lastAlertTimeMap = new ConcurrentHashMap<>();

    private static final DateTimeFormatter RECORD_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Transactional
    public VideoQualityRecord saveDetectionResult(VideoQualityAnalysisResult result) {
        VideoQualityRecord record = new VideoQualityRecord();
        record.setRecordNo("VQR" + LocalDateTime.now().format(RECORD_NO_FORMATTER)
                + String.format("%04d", (int) (Math.random() * 10000)));
        record.setCameraId(result.getCameraId());
        record.setCameraName(result.getCameraName());

        Camera camera = cameraService.getById(result.getCameraId());
        if (camera != null) {
            record.setCameraCode(camera.getCameraCode());
        }

        record.setDetectionTime(result.getDetectionTime() != null ? result.getDetectionTime() : LocalDateTime.now());
        record.setFrameUrl(result.getFrameUrl());
        record.setBrightness(result.getBrightness());
        record.setBrightnessLevel(result.getBrightnessLevel());
        record.setContrast(result.getContrast());
        record.setContrastLevel(result.getContrastLevel());
        record.setBlurScore(result.getBlurScore());
        record.setBlurLevel(result.getBlurLevel());
        record.setOcclusionRatio(result.getOcclusionRatio());
        record.setOcclusionLevel(result.getOcclusionLevel());
        record.setOcclusionRegions(result.getOcclusionRegions());
        record.setIsBlackScreen(Boolean.TRUE.equals(result.getIsBlackScreen()) ? 1 : 0);
        record.setIsFrozen(Boolean.TRUE.equals(result.getIsFrozen()) ? 1 : 0);
        record.setFreezeDuration(result.getFreezeDuration());
        record.setFrameChangeRate(result.getFrameChangeRate());
        record.setNoiseLevel(result.getNoiseLevel());
        record.setColorCastLevel(result.getColorCastLevel());
        record.setOverallScore(result.getOverallScore());
        record.setQualityLevel(result.getQualityLevel());
        record.setIsAbnormal(Boolean.TRUE.equals(result.getIsAbnormal()) ? 1 : 0);
        record.setAbnormalTypes(result.getAbnormalTypes());
        record.setAlertTriggered(0);
        record.setDetectionDurationMs(result.getDetectionDurationMs());
        record.setAlgorithmVersion(result.getAlgorithmVersion());
        record.setDescription(result.getDescription());

        recordMapper.insert(record);
        log.info("保存视频质量检测记录: cameraId={}, recordNo={}, abnormal={}, score={}",
                result.getCameraId(), record.getRecordNo(), result.getIsAbnormal(), result.getOverallScore());

        if (Boolean.TRUE.equals(result.getIsAbnormal())) {
            try {
                boolean shouldAlert = checkAndTriggerAlert(camera, record, result);
                record.setAlertTriggered(shouldAlert ? 1 : 0);
                if (record.getAlertEventId() != null) {
                    recordMapper.updateById(record);
                }
            } catch (Exception e) {
                log.error("处理视频质量告警失败: cameraId={}, recordNo={}, err={}",
                        result.getCameraId(), record.getRecordNo(), e.getMessage());
            }
        } else {
            resetAbnormalCounter(result.getCameraId());
        }

        return record;
    }

    private boolean checkAndTriggerAlert(Camera camera, VideoQualityRecord record, VideoQualityAnalysisResult result) {
        if (camera == null) return false;

        Long cameraId = camera.getId();
        long now = System.currentTimeMillis();
        Long lastAlertTime = lastAlertTimeMap.get(cameraId);
        long cooldownMs = config.getAlertCooldownMinutes() * 60 * 1000L;
        if (lastAlertTime != null && (now - lastAlertTime) < cooldownMs) {
            log.debug("告警冷却中，跳过: cameraId={}, lastAlert={}ms ago", cameraId, (now - lastAlertTime));
            return false;
        }

        AbnormalCounter counter = abnormalCounterMap.computeIfAbsent(cameraId, k -> new AbnormalCounter());
        Set<String> currentAbnormalTypes = new HashSet<>();
        if (result.getAbnormalTypes() != null && !result.getAbnormalTypes().isEmpty()) {
            currentAbnormalTypes.addAll(Arrays.asList(result.getAbnormalTypes().split(",")));
        }

        counter.abnormalTypes.addAll(currentAbnormalTypes);
        counter.consecutiveCount++;
        counter.lastAbnormalTime = now;

        if (counter.consecutiveCount < config.getAbnormalTriggerThreshold()) {
            log.debug("异常次数未达到阈值: cameraId={}, count={}, threshold={}",
                    cameraId, counter.consecutiveCount, config.getAbnormalTriggerThreshold());
            return false;
        }

        boolean isCritical = currentAbnormalTypes.contains("BLACK_SCREEN")
                || currentAbnormalTypes.contains("FREEZE")
                || (result.getQualityLevel() != null && result.getQualityLevel() >= 4);

        String eventType = resolveEventType(currentAbnormalTypes);
        int eventLevel = isCritical ? 3 : 2;

        try {
            AiEventCallbackRequest callback = buildQualityAlertCallback(camera, record, eventType, eventLevel, counter);
            AlertEvent alertEvent = alertEventService.handleAiEventCallback(callback);

            if (alertEvent != null && alertEvent.getId() != null) {
                record.setAlertEventId(alertEvent.getId());
                lastAlertTimeMap.put(cameraId, now);
                counter.consecutiveCount = 0;
                counter.abnormalTypes.clear();
                log.warn("视频质量告警已生成: cameraId={}, eventNo={}, type={}, level={}",
                        cameraId, alertEvent.getEventNo(), eventType, eventLevel);
                return true;
            }
        } catch (Exception e) {
            log.error("生成视频质量告警异常: cameraId={}, err={}", cameraId, e.getMessage());
        }

        return false;
    }

    private String resolveEventType(Set<String> abnormalTypes) {
        if (abnormalTypes.contains("BLACK_SCREEN")) return "BLACK_SCREEN";
        if (abnormalTypes.contains("FREEZE")) return "VIDEO_FREEZE";
        if (abnormalTypes.contains("OCCLUSION")) return "VIDEO_OCCLUSION";
        if (abnormalTypes.contains("BLUR")) return "VIDEO_BLUR";
        return "VIDEO_QUALITY_ABNORMAL";
    }

    private AiEventCallbackRequest buildQualityAlertCallback(Camera camera, VideoQualityRecord record,
                                                              String eventType, int eventLevel,
                                                              AbnormalCounter counter) {
        AiEventCallbackRequest req = new AiEventCallbackRequest();
        req.setCameraId(camera.getId());
        req.setEventType(eventType);
        req.setEventLevel(eventLevel);
        req.setEventTime(record.getDetectionTime());
        req.setConfidence(BigDecimal.valueOf(0.95));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("quality_level", record.getQualityLevel());
        metadata.put("overall_score", record.getOverallScore());
        metadata.put("brightness", record.getBrightness());
        metadata.put("contrast", record.getContrast());
        metadata.put("blur_score", record.getBlurScore());
        metadata.put("occlusion_ratio", record.getOcclusionRatio());
        metadata.put("is_black_screen", record.getIsBlackScreen());
        metadata.put("is_frozen", record.getIsFrozen());
        metadata.put("freeze_duration", record.getFreezeDuration());
        metadata.put("abnormal_types", record.getAbnormalTypes());
        metadata.put("consecutive_count", counter.consecutiveCount);
        metadata.put("source", "VIDEO_QUALITY_DIAGNOSIS");
        req.setMetadata(metadata);

        StringBuilder desc = new StringBuilder();
        desc.append("视频质量异常告警: ");
        if ("BLACK_SCREEN".equals(eventType)) {
            desc.append("摄像头画面黑屏");
        } else if ("VIDEO_FREEZE".equals(eventType)) {
            desc.append("画面冻结，持续约").append(record.getFreezeDuration()).append("秒");
        } else if ("VIDEO_OCCLUSION".equals(eventType)) {
            desc.append("画面遮挡率").append(record.getOcclusionRatio()).append("%");
        } else if ("VIDEO_BLUR".equals(eventType)) {
            desc.append("画面模糊，清晰度评分").append(record.getBlurScore());
        } else {
            desc.append("综合评分").append(record.getOverallScore());
        }
        if (record.getDescription() != null) {
            desc.append(" | ").append(record.getDescription());
        }
        req.setDescription(desc.toString());

        return req;
    }

    private void resetAbnormalCounter(Long cameraId) {
        AbnormalCounter counter = abnormalCounterMap.get(cameraId);
        if (counter != null) {
            counter.consecutiveCount = 0;
            counter.abnormalTypes.clear();
        }
    }

    public PageResult<VideoQualityRecord> pageRecords(VideoQualityRecordQuery query) {
        Page<VideoQualityRecord> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<VideoQualityRecord> wrapper = new LambdaQueryWrapper<>();

        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(VideoQualityRecord::getCameraName, query.getKeyword())
                    .or().like(VideoQualityRecord::getCameraCode, query.getKeyword())
                    .or().like(VideoQualityRecord::getRecordNo, query.getKeyword());
        }
        if (query.getCameraId() != null) {
            wrapper.eq(VideoQualityRecord::getCameraId, query.getCameraId());
        }
        if (query.getCameraCode() != null && !query.getCameraCode().isEmpty()) {
            wrapper.eq(VideoQualityRecord::getCameraCode, query.getCameraCode());
        }
        if (query.getQualityLevel() != null) {
            wrapper.eq(VideoQualityRecord::getQualityLevel, query.getQualityLevel());
        }
        if (query.getIsAbnormal() != null) {
            wrapper.eq(VideoQualityRecord::getIsAbnormal, query.getIsAbnormal());
        }
        if (query.getAbnormalType() != null && !query.getAbnormalType().isEmpty()) {
            wrapper.like(VideoQualityRecord::getAbnormalTypes, query.getAbnormalType());
        }
        if (query.getAlertTriggered() != null) {
            wrapper.eq(VideoQualityRecord::getAlertTriggered, query.getAlertTriggered());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(VideoQualityRecord::getDetectionTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(VideoQualityRecord::getDetectionTime, query.getEndTime());
        }
        wrapper.orderByDesc(VideoQualityRecord::getDetectionTime);

        recordMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
    }

    public PageResult<VideoHealthDiagnosis> pageDiagnosis(VideoHealthDiagnosisQuery query) {
        Page<VideoHealthDiagnosis> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<VideoHealthDiagnosis> wrapper = new LambdaQueryWrapper<>();

        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(VideoHealthDiagnosis::getCameraName, query.getKeyword())
                    .or().like(VideoHealthDiagnosis::getCameraCode, query.getKeyword())
                    .or().like(VideoHealthDiagnosis::getRoadName, query.getKeyword());
        }
        if (query.getCameraId() != null) {
            wrapper.eq(VideoHealthDiagnosis::getCameraId, query.getCameraId());
        }
        if (query.getCameraCode() != null && !query.getCameraCode().isEmpty()) {
            wrapper.eq(VideoHealthDiagnosis::getCameraCode, query.getCameraCode());
        }
        if (query.getRoadName() != null && !query.getRoadName().isEmpty()) {
            wrapper.like(VideoHealthDiagnosis::getRoadName, query.getRoadName());
        }
        if (query.getHealthLevel() != null) {
            wrapper.eq(VideoHealthDiagnosis::getHealthLevel, query.getHealthLevel());
        }
        if (query.getMaintenanceStatus() != null) {
            wrapper.eq(VideoHealthDiagnosis::getMaintenanceStatus, query.getMaintenanceStatus());
        }
        if (query.getPeriodType() != null && !query.getPeriodType().isEmpty()) {
            wrapper.eq(VideoHealthDiagnosis::getPeriodType, query.getPeriodType());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(VideoHealthDiagnosis::getDiagnosisDate, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(VideoHealthDiagnosis::getDiagnosisDate, query.getEndDate());
        }
        wrapper.orderByDesc(VideoHealthDiagnosis::getDiagnosisDate)
                .orderByDesc(VideoHealthDiagnosis::getHealthLevel);

        diagnosisMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
    }

    public Map<String, Object> getHealthDashboard() {
        List<Camera> cameras = cameraService.list();
        int totalCameras = cameras.size();

        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<VideoHealthDiagnosis> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.eq(VideoHealthDiagnosis::getDiagnosisDate, today)
                .eq(VideoHealthDiagnosis::getPeriodType, "DAILY");
        List<VideoHealthDiagnosis> todayDiagnoses = diagnosisMapper.selectList(todayWrapper);

        int healthy = 0, subhealthy = 0, abnormal = 0, critical = 0, faulty = 0;
        int needMaintenance = 0;
        BigDecimal totalHealthScore = BigDecimal.ZERO;
        int scoreCount = 0;

        Map<String, Integer> abnormalTypeStats = new LinkedHashMap<>();
        abnormalTypeStats.put("BLACK_SCREEN", 0);
        abnormalTypeStats.put("VIDEO_FREEZE", 0);
        abnormalTypeStats.put("VIDEO_OCCLUSION", 0);
        abnormalTypeStats.put("VIDEO_BLUR", 0);
        abnormalTypeStats.put("LOW_BRIGHTNESS", 0);
        abnormalTypeStats.put("LOW_CONTRAST", 0);

        for (VideoHealthDiagnosis d : todayDiagnoses) {
            int level = d.getHealthLevel() != null ? d.getHealthLevel() : 1;
            switch (level) {
                case 1 -> healthy++;
                case 2 -> subhealthy++;
                case 3 -> abnormal++;
                case 4 -> critical++;
                case 5 -> faulty++;
            }
            if (d.getMaintenanceStatus() != null && d.getMaintenanceStatus() >= 1) {
                needMaintenance++;
            }
            if (d.getHealthScore() != null) {
                totalHealthScore = totalHealthScore.add(d.getHealthScore());
                scoreCount++;
            }
            abnormalTypeStats.merge("BLACK_SCREEN", d.getBlackScreenCount() != null ? d.getBlackScreenCount() : 0, Integer::sum);
            abnormalTypeStats.merge("VIDEO_FREEZE", d.getFreezeCount() != null ? d.getFreezeCount() : 0, Integer::sum);
            abnormalTypeStats.merge("VIDEO_OCCLUSION", d.getOcclusionCount() != null ? d.getOcclusionCount() : 0, Integer::sum);
            abnormalTypeStats.merge("VIDEO_BLUR", d.getBlurCount() != null ? d.getBlurCount() : 0, Integer::sum);
            abnormalTypeStats.merge("LOW_BRIGHTNESS", d.getLowBrightnessCount() != null ? d.getLowBrightnessCount() : 0, Integer::sum);
            abnormalTypeStats.merge("LOW_CONTRAST", d.getLowContrastCount() != null ? d.getLowContrastCount() : 0, Integer::sum);
        }

        int diagnosed = todayDiagnoses.size();
        int undiagnosed = totalCameras - diagnosed;

        BigDecimal avgHealthScore = scoreCount > 0
                ? totalHealthScore.divide(BigDecimal.valueOf(scoreCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        LambdaQueryWrapper<VideoQualityRecord> abnormalRecordWrapper = new LambdaQueryWrapper<>();
        abnormalRecordWrapper.eq(VideoQualityRecord::getIsAbnormal, 1)
                .ge(VideoQualityRecord::getDetectionTime, today.atStartOfDay())
                .orderByDesc(VideoQualityRecord::getDetectionTime)
                .last("LIMIT 10");
        List<VideoQualityRecord> recentAbnormalRecords = recordMapper.selectList(abnormalRecordWrapper);

        List<Map<String, Object>> cameraHealthList = new ArrayList<>();
        for (VideoHealthDiagnosis d : todayDiagnoses) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("cameraId", d.getCameraId());
            item.put("cameraName", d.getCameraName());
            item.put("cameraCode", d.getCameraCode());
            item.put("roadName", d.getRoadName());
            item.put("longitude", d.getLongitude());
            item.put("latitude", d.getLatitude());
            item.put("healthScore", d.getHealthScore());
            item.put("healthLevel", d.getHealthLevel());
            item.put("healthLevelLabel", d.getHealthLevelLabel());
            item.put("normalRate", d.getNormalRate());
            item.put("avgOverallScore", d.getAvgOverallScore());
            item.put("maintenanceStatus", d.getMaintenanceStatus());
            item.put("recommendation", d.getRecommendation());
            item.put("alertCount", d.getAlertCount());
            cameraHealthList.add(item);
        }

        cameraHealthList.sort((a, b) -> {
            BigDecimal sa = (BigDecimal) a.get("healthScore");
            BigDecimal sb = (BigDecimal) b.get("healthScore");
            if (sa == null) sa = BigDecimal.ZERO;
            if (sb == null) sb = BigDecimal.ZERO;
            return sa.compareTo(sb);
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCameras", totalCameras);
        result.put("diagnosedCount", diagnosed);
        result.put("undiagnosedCount", undiagnosed);
        result.put("healthyCount", healthy);
        result.put("subhealthyCount", subhealthy);
        result.put("abnormalCount", abnormal);
        result.put("criticalCount", critical);
        result.put("faultyCount", faulty);
        result.put("needMaintenanceCount", needMaintenance);
        result.put("avgHealthScore", avgHealthScore);
        result.put("onlineEstimate", totalCameras > 0
                ? BigDecimal.valueOf((double) (healthy + subhealthy) / totalCameras * 100).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        result.put("abnormalTypeStats", abnormalTypeStats);
        result.put("recentAbnormalRecords", recentAbnormalRecords);
        result.put("cameraHealthList", cameraHealthList);
        result.put("reportDate", today);

        return result;
    }

    public Map<String, Object> getPatrolReport(LocalDate startDate, LocalDate endDate, String periodType) {
        if (startDate == null) startDate = LocalDate.now().minusDays(7);
        if (endDate == null) endDate = LocalDate.now();
        if (periodType == null) periodType = "DAILY";

        LambdaQueryWrapper<VideoHealthDiagnosis> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoHealthDiagnosis::getPeriodType, periodType)
                .between(VideoHealthDiagnosis::getDiagnosisDate, startDate, endDate)
                .orderByAsc(VideoHealthDiagnosis::getDiagnosisDate)
                .orderByAsc(VideoHealthDiagnosis::getCameraId);
        List<VideoHealthDiagnosis> diagnoses = diagnosisMapper.selectList(wrapper);

        List<Camera> allCameras = cameraService.list();
        Map<Long, Camera> cameraMap = new HashMap<>();
        for (Camera c : allCameras) cameraMap.put(c.getId(), c);

        Map<LocalDate, List<VideoHealthDiagnosis>> dateGrouped = new LinkedHashMap<>();
        for (VideoHealthDiagnosis d : diagnoses) {
            dateGrouped.computeIfAbsent(d.getDiagnosisDate(), k -> new ArrayList<>()).add(d);
        }

        List<Map<String, Object>> dailyStats = new ArrayList<>();
        for (Map.Entry<LocalDate, List<VideoHealthDiagnosis>> entry : dateGrouped.entrySet()) {
            LocalDate date = entry.getKey();
            List<VideoHealthDiagnosis> list = entry.getValue();
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("date", date);

            BigDecimal avgScore = list.stream()
                    .filter(d -> d.getAvgOverallScore() != null)
                    .map(VideoHealthDiagnosis::getAvgOverallScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int scoreCount = (int) list.stream().filter(d -> d.getAvgOverallScore() != null).count();
            stat.put("avgOverallScore", scoreCount > 0
                    ? avgScore.divide(BigDecimal.valueOf(scoreCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);

            BigDecimal avgHealth = list.stream()
                    .filter(d -> d.getHealthScore() != null)
                    .map(VideoHealthDiagnosis::getHealthScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int hCount = (int) list.stream().filter(d -> d.getHealthScore() != null).count();
            stat.put("avgHealthScore", hCount > 0
                    ? avgHealth.divide(BigDecimal.valueOf(hCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);

            int abnormalTotal = list.stream()
                    .mapToInt(d -> d.getAbnormalCount() != null ? d.getAbnormalCount() : 0).sum();
            int detectionTotal = list.stream()
                    .mapToInt(d -> d.getTotalDetectionCount() != null ? d.getTotalDetectionCount() : 0).sum();
            stat.put("totalDetectionCount", detectionTotal);
            stat.put("totalAbnormalCount", abnormalTotal);
            stat.put("normalRate", detectionTotal > 0
                    ? BigDecimal.valueOf((double) (detectionTotal - abnormalTotal) / detectionTotal * 100).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100));

            int alertTotal = list.stream()
                    .mapToInt(d -> d.getAlertCount() != null ? d.getAlertCount() : 0).sum();
            stat.put("totalAlertCount", alertTotal);

            Map<String, Integer> types = new LinkedHashMap<>();
            types.put("blackScreen", list.stream().mapToInt(d -> d.getBlackScreenCount() != null ? d.getBlackScreenCount() : 0).sum());
            types.put("freeze", list.stream().mapToInt(d -> d.getFreezeCount() != null ? d.getFreezeCount() : 0).sum());
            types.put("occlusion", list.stream().mapToInt(d -> d.getOcclusionCount() != null ? d.getOcclusionCount() : 0).sum());
            types.put("blur", list.stream().mapToInt(d -> d.getBlurCount() != null ? d.getBlurCount() : 0).sum());
            types.put("lowBrightness", list.stream().mapToInt(d -> d.getLowBrightnessCount() != null ? d.getLowBrightnessCount() : 0).sum());
            types.put("lowContrast", list.stream().mapToInt(d -> d.getLowContrastCount() != null ? d.getLowContrastCount() : 0).sum());
            stat.put("abnormalBreakdown", types);

            int h1 = 0, h2 = 0, h3 = 0, h4 = 0, h5 = 0;
            for (VideoHealthDiagnosis d : list) {
                int l = d.getHealthLevel() != null ? d.getHealthLevel() : 1;
                if (l == 1) h1++;
                else if (l == 2) h2++;
                else if (l == 3) h3++;
                else if (l == 4) h4++;
                else h5++;
            }
            Map<String, Integer> hl = new LinkedHashMap<>();
            hl.put("healthy", h1);
            hl.put("subhealthy", h2);
            hl.put("abnormal", h3);
            hl.put("critical", h4);
            hl.put("faulty", h5);
            stat.put("healthLevelBreakdown", hl);

            dailyStats.add(stat);
        }

        List<Map<String, Object>> perCameraReport = new ArrayList<>();
        for (Camera camera : allCameras) {
            List<VideoHealthDiagnosis> camList = diagnoses.stream()
                    .filter(d -> camera.getId().equals(d.getCameraId()))
                    .toList();

            Map<String, Object> cam = new LinkedHashMap<>();
            cam.put("cameraId", camera.getId());
            cam.put("cameraName", camera.getCameraName());
            cam.put("cameraCode", camera.getCameraCode());
            cam.put("roadName", camera.getRoadName());
            cam.put("location", camera.getLocation());
            cam.put("manufacturer", camera.getManufacturer());

            if (camList.isEmpty()) {
                cam.put("reportStatus", "NO_DATA");
            } else {
                cam.put("reportStatus", "OK");

                VideoHealthDiagnosis latest = camList.get(camList.size() - 1);
                cam.put("latestHealthScore", latest.getHealthScore());
                cam.put("latestHealthLevel", latest.getHealthLevel());
                cam.put("latestHealthLevelLabel", latest.getHealthLevelLabel());

                BigDecimal avgS = camList.stream()
                        .filter(d -> d.getHealthScore() != null)
                        .map(VideoHealthDiagnosis::getHealthScore)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                int cnt = (int) camList.stream().filter(d -> d.getHealthScore() != null).count();
                cam.put("periodAvgHealthScore", cnt > 0
                        ? avgS.divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);

                int totalAb = camList.stream().mapToInt(d -> d.getAbnormalCount() != null ? d.getAbnormalCount() : 0).sum();
                int totalDet = camList.stream().mapToInt(d -> d.getTotalDetectionCount() != null ? d.getTotalDetectionCount() : 0).sum();
                cam.put("totalDetectionCount", totalDet);
                cam.put("totalAbnormalCount", totalAb);
                cam.put("abnormalRate", totalDet > 0
                        ? BigDecimal.valueOf((double) totalAb / totalDet * 100).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);
                cam.put("totalAlertCount", camList.stream()
                        .mapToInt(d -> d.getAlertCount() != null ? d.getAlertCount() : 0).sum());
                cam.put("maintenanceStatus", latest.getMaintenanceStatus());
                cam.put("recommendation", latest.getRecommendation());
            }
            perCameraReport.add(cam);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("startDate", startDate);
        report.put("endDate", endDate);
        report.put("periodType", periodType);
        report.put("totalCameras", allCameras.size());
        report.put("dailyStats", dailyStats);
        report.put("perCameraReport", perCameraReport);
        return report;
    }

    @Transactional
    public VideoHealthDiagnosis generateDailyDiagnosis(Long cameraId, LocalDate date) {
        Camera camera = cameraService.getById(cameraId);
        if (camera == null) {
            throw new BusinessException("摄像头不存在");
        }
        if (date == null) date = LocalDate.now();

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        LambdaQueryWrapper<VideoQualityRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoQualityRecord::getCameraId, cameraId)
                .between(VideoQualityRecord::getDetectionTime, startOfDay, endOfDay);
        List<VideoQualityRecord> records = recordMapper.selectList(wrapper);

        VideoHealthDiagnosis diagnosis = new VideoHealthDiagnosis();
        diagnosis.setDiagnosisNo("VHD" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%04d", cameraId));
        diagnosis.setCameraId(cameraId);
        diagnosis.setCameraName(camera.getCameraName());
        diagnosis.setCameraCode(camera.getCameraCode());
        diagnosis.setRoadName(camera.getRoadName());
        diagnosis.setLongitude(camera.getLongitude());
        diagnosis.setLatitude(camera.getLatitude());
        diagnosis.setDiagnosisDate(date);
        diagnosis.setPeriodType("DAILY");
        diagnosis.setStatus(1);

        if (records.isEmpty()) {
            diagnosis.setTotalDetectionCount(0);
            diagnosis.setAbnormalCount(0);
            diagnosis.setNormalRate(BigDecimal.ZERO);
            diagnosis.setHealthScore(BigDecimal.valueOf(50));
            diagnosis.setHealthLevel(3);
            diagnosis.setHealthLevelLabel("异常");
            diagnosis.setMaintenanceStatus(2);
            diagnosis.setRecommendation("当日无检测数据，请检查定时任务或摄像头在线状态");
        } else {
            fillDiagnosisFromRecords(diagnosis, records);
        }

        LambdaQueryWrapper<VideoHealthDiagnosis> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(VideoHealthDiagnosis::getCameraId, cameraId)
                .eq(VideoHealthDiagnosis::getDiagnosisDate, date)
                .eq(VideoHealthDiagnosis::getPeriodType, "DAILY");
        VideoHealthDiagnosis exist = diagnosisMapper.selectOne(existWrapper);
        if (exist != null) {
            diagnosis.setId(exist.getId());
            diagnosisMapper.updateById(diagnosis);
        } else {
            diagnosisMapper.insert(diagnosis);
        }

        log.info("生成摄像头健康度日诊断: cameraId={}, date={}, healthScore={}, level={}",
                cameraId, date, diagnosis.getHealthScore(), diagnosis.getHealthLevel());
        return diagnosis;
    }

    @Transactional
    public void generateAllDailyDiagnosis(LocalDate date) {
        if (date == null) date = LocalDate.now();
        List<Camera> cameras = cameraService.list();
        log.info("开始批量生成日诊断报告: date={}, cameraCount={}", date, cameras.size());
        int success = 0;
        for (Camera camera : cameras) {
            try {
                generateDailyDiagnosis(camera.getId(), date);
                success++;
            } catch (Exception e) {
                log.error("生成日诊断失败: cameraId={}, err={}", camera.getId(), e.getMessage());
            }
        }
        log.info("批量生成日诊断完成: date={}, success={}/{}", date, success, cameras.size());
    }

    @Transactional
    public VideoHealthDiagnosis generateWeeklyDiagnosis(Long cameraId, LocalDate weekStartDate) {
        Camera camera = cameraService.getById(cameraId);
        if (camera == null) throw new BusinessException("摄像头不存在");
        if (weekStartDate == null) {
            weekStartDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        }
        LocalDate weekEndDate = weekStartDate.plusDays(6);

        LambdaQueryWrapper<VideoHealthDiagnosis> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoHealthDiagnosis::getCameraId, cameraId)
                .eq(VideoHealthDiagnosis::getPeriodType, "DAILY")
                .between(VideoHealthDiagnosis::getDiagnosisDate, weekStartDate, weekEndDate);
        List<VideoHealthDiagnosis> dailyList = diagnosisMapper.selectList(wrapper);

        LambdaQueryWrapper<VideoQualityRecord> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(VideoQualityRecord::getCameraId, cameraId)
                .between(VideoQualityRecord::getDetectionTime, weekStartDate.atStartOfDay(), weekEndDate.plusDays(1).atStartOfDay());
        List<VideoQualityRecord> allRecords = recordMapper.selectList(recordWrapper);

        VideoHealthDiagnosis weekly = new VideoHealthDiagnosis();
        weekly.setDiagnosisNo("VHDW" + weekStartDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%04d", cameraId));
        weekly.setCameraId(cameraId);
        weekly.setCameraName(camera.getCameraName());
        weekly.setCameraCode(camera.getCameraCode());
        weekly.setRoadName(camera.getRoadName());
        weekly.setLongitude(camera.getLongitude());
        weekly.setLatitude(camera.getLatitude());
        weekly.setDiagnosisDate(weekStartDate);
        weekly.setPeriodType("WEEKLY");
        weekly.setStatus(1);

        if (!allRecords.isEmpty()) {
            fillDiagnosisFromRecords(weekly, allRecords);
        } else if (!dailyList.isEmpty()) {
            weekly.setTotalDetectionCount(dailyList.stream()
                    .mapToInt(d -> d.getTotalDetectionCount() != null ? d.getTotalDetectionCount() : 0).sum());
            weekly.setAbnormalCount(dailyList.stream()
                    .mapToInt(d -> d.getAbnormalCount() != null ? d.getAbnormalCount() : 0).sum());
            int det = weekly.getTotalDetectionCount();
            int abn = weekly.getAbnormalCount();
            weekly.setNormalRate(det > 0
                    ? BigDecimal.valueOf((double) (det - abn) / det * 100).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100));
            BigDecimal avgH = dailyList.stream()
                    .filter(d -> d.getHealthScore() != null)
                    .map(VideoHealthDiagnosis::getHealthScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int cnt = (int) dailyList.stream().filter(d -> d.getHealthScore() != null).count();
            BigDecimal hs = cnt > 0 ? avgH.divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP) : BigDecimal.valueOf(50);
            weekly.setHealthScore(hs);
            weekly.setHealthLevel(classifyHealthLevel(hs));
            weekly.setHealthLevelLabel(getHealthLevelLabel(weekly.getHealthLevel()));
            weekly.setAlertCount(dailyList.stream()
                    .mapToInt(d -> d.getAlertCount() != null ? d.getAlertCount() : 0).sum());
        } else {
            weekly.setHealthScore(BigDecimal.valueOf(50));
            weekly.setHealthLevel(3);
            weekly.setHealthLevelLabel("异常");
            weekly.setMaintenanceStatus(2);
        }

        LambdaQueryWrapper<VideoHealthDiagnosis> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(VideoHealthDiagnosis::getCameraId, cameraId)
                .eq(VideoHealthDiagnosis::getDiagnosisDate, weekStartDate)
                .eq(VideoHealthDiagnosis::getPeriodType, "WEEKLY");
        VideoHealthDiagnosis exist = diagnosisMapper.selectOne(existWrapper);
        if (exist != null) {
            weekly.setId(exist.getId());
            diagnosisMapper.updateById(weekly);
        } else {
            diagnosisMapper.insert(weekly);
        }

        log.info("生成摄像头健康度周诊断: cameraId={}, weekStart={}, healthScore={}",
                cameraId, weekStartDate, weekly.getHealthScore());
        return weekly;
    }

    private void fillDiagnosisFromRecords(VideoHealthDiagnosis diagnosis, List<VideoQualityRecord> records) {
        int total = records.size();
        int abnormal = (int) records.stream().filter(r -> r.getIsAbnormal() != null && r.getIsAbnormal() == 1).count();

        diagnosis.setTotalDetectionCount(total);
        diagnosis.setAbnormalCount(abnormal);
        diagnosis.setNormalRate(total > 0
                ? BigDecimal.valueOf((double) (total - abnormal) / total * 100).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100));

        diagnosis.setAvgBrightness(avg(records, VideoQualityRecord::getBrightness));
        diagnosis.setAvgContrast(avg(records, VideoQualityRecord::getContrast));
        diagnosis.setAvgBlurScore(avg(records, VideoQualityRecord::getBlurScore));
        diagnosis.setAvgOcclusionRatio(avg(records, VideoQualityRecord::getOcclusionRatio));
        diagnosis.setAvgOverallScore(avg(records, VideoQualityRecord::getOverallScore));

        Optional<VideoQualityRecord> minOpt = records.stream()
                .filter(r -> r.getOverallScore() != null)
                .min(Comparator.comparing(VideoQualityRecord::getOverallScore));
        diagnosis.setMinOverallScore(minOpt.map(VideoQualityRecord::getOverallScore).orElse(BigDecimal.ZERO));

        Optional<VideoQualityRecord> maxOpt = records.stream()
                .filter(r -> r.getOverallScore() != null)
                .max(Comparator.comparing(VideoQualityRecord::getOverallScore));
        diagnosis.setMaxOverallScore(maxOpt.map(VideoQualityRecord::getOverallScore).orElse(BigDecimal.ZERO));

        diagnosis.setBlackScreenCount((int) records.stream()
                .filter(r -> r.getIsBlackScreen() != null && r.getIsBlackScreen() == 1).count());
        diagnosis.setBlackScreenDuration(records.stream()
                .filter(r -> r.getIsBlackScreen() != null && r.getIsBlackScreen() == 1)
                .mapToInt(r -> config.getDetectionIntervalMinutes() * 60).sum());
        diagnosis.setBlurCount((int) records.stream()
                .filter(r -> r.getBlurLevel() != null && r.getBlurLevel() >= 2).count());
        diagnosis.setOcclusionCount((int) records.stream()
                .filter(r -> r.getOcclusionLevel() != null && r.getOcclusionLevel() >= 2).count());
        diagnosis.setFreezeCount((int) records.stream()
                .filter(r -> r.getIsFrozen() != null && r.getIsFrozen() == 1).count());
        diagnosis.setFreezeDuration(records.stream()
                .mapToInt(r -> r.getFreezeDuration() != null ? r.getFreezeDuration() : 0).sum());
        diagnosis.setLowBrightnessCount((int) records.stream()
                .filter(r -> r.getBrightnessLevel() != null && r.getBrightnessLevel() == 2).count());
        diagnosis.setHighBrightnessCount((int) records.stream()
                .filter(r -> r.getBrightnessLevel() != null && r.getBrightnessLevel() == 3).count());
        diagnosis.setLowContrastCount((int) records.stream()
                .filter(r -> r.getContrastLevel() != null && r.getContrastLevel() == 2).count());
        diagnosis.setAlertCount((int) records.stream()
                .filter(r -> r.getAlertTriggered() != null && r.getAlertTriggered() == 1).count());

        BigDecimal avgScore = diagnosis.getAvgOverallScore();
        BigDecimal normalRate = diagnosis.getNormalRate();
        double abnormalRatio = abnormal > 0 ? (double) abnormal / total : 0;
        double weightedScore = 0.6 * avgScore.doubleValue() + 0.4 * normalRate.doubleValue();
        if (diagnosis.getBlackScreenCount() > 0) weightedScore -= 20;
        if (diagnosis.getFreezeCount() > 0) weightedScore -= 10;
        if (abnormalRatio > 0.5) weightedScore -= 15;
        weightedScore = Math.max(0, Math.min(100, weightedScore));
        BigDecimal healthScore = BigDecimal.valueOf(weightedScore).setScale(2, RoundingMode.HALF_UP);

        diagnosis.setHealthScore(healthScore);
        diagnosis.setHealthLevel(classifyHealthLevel(healthScore));
        diagnosis.setHealthLevelLabel(getHealthLevelLabel(diagnosis.getHealthLevel()));

        long totalSeconds = (long) total * config.getDetectionIntervalMinutes() * 60;
        long abnormalSeconds = (long) abnormal * config.getDetectionIntervalMinutes() * 60;
        long uptime = Math.max(0, totalSeconds - abnormalSeconds);
        diagnosis.setUptimeSeconds(uptime);
        diagnosis.setDowntimeSeconds(abnormalSeconds);
        diagnosis.setOnlineRate(totalSeconds > 0
                ? BigDecimal.valueOf((double) uptime / totalSeconds * 100).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        int maintenance = determineMaintenanceStatus(diagnosis);
        diagnosis.setMaintenanceStatus(maintenance);
        diagnosis.setRecommendation(generateRecommendation(diagnosis));
    }

    private BigDecimal avg(List<VideoQualityRecord> records,
                           java.util.function.Function<VideoQualityRecord, BigDecimal> getter) {
        List<BigDecimal> values = records.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .toList();
        if (values.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private int classifyHealthLevel(BigDecimal score) {
        VideoQualityConfig.Scoring s = config.getScoring();
        double v = score.doubleValue();
        if (v >= s.getHealthExcellent().doubleValue()) return 1;
        if (v >= s.getHealthSubhealthy().doubleValue()) return 2;
        if (v >= s.getHealthAbnormal().doubleValue()) return 3;
        if (v >= s.getHealthCritical().doubleValue()) return 4;
        return 5;
    }

    private String getHealthLevelLabel(Integer level) {
        if (level == null) return "未知";
        return switch (level) {
            case 1 -> "健康";
            case 2 -> "亚健康";
            case 3 -> "异常";
            case 4 -> "严重异常";
            case 5 -> "故障";
            default -> "未知";
        };
    }

    private int determineMaintenanceStatus(VideoHealthDiagnosis d) {
        if (d.getHealthLevel() != null && d.getHealthLevel() >= 5) return 2;
        if (d.getBlackScreenCount() != null && d.getBlackScreenCount() > 0) return 2;
        if (d.getFreezeCount() != null && d.getFreezeCount() > 10) return 2;
        if (d.getHealthLevel() != null && d.getHealthLevel() >= 4) return 2;
        if (d.getOcclusionCount() != null && d.getOcclusionCount() > 20) return 1;
        if (d.getBlurCount() != null && d.getBlurCount() > 20) return 1;
        if (d.getHealthLevel() != null && d.getHealthLevel() == 3) return 1;
        return 0;
    }

    private String generateRecommendation(VideoHealthDiagnosis d) {
        List<String> recs = new ArrayList<>();
        if (d.getBlackScreenCount() != null && d.getBlackScreenCount() > 0) {
            recs.add("出现黑屏，请立即检查摄像头电源、网线及视频输出");
        }
        if (d.getFreezeCount() != null && d.getFreezeCount() > 5) {
            recs.add("画面频繁冻结，请检查视频流稳定性和编码器状态");
        }
        if (d.getOcclusionCount() != null && d.getOcclusionCount() > 10) {
            recs.add("画面存在遮挡，请现场检查镜头是否被异物遮挡或需要清洁");
        }
        if (d.getBlurCount() != null && d.getBlurCount() > 10) {
            recs.add("画面模糊，请检查镜头焦距和聚焦状态");
        }
        if (d.getLowBrightnessCount() != null && d.getLowBrightnessCount() > 15) {
            recs.add("亮度偏低，请检查夜间补光灯或调整摄像机曝光参数");
        }
        if (d.getLowContrastCount() != null && d.getLowContrastCount() > 15) {
            recs.add("对比度偏低，建议调整图像增强参数");
        }
        if (recs.isEmpty()) {
            recs.add("运行状态良好，按计划进行常规维护即可");
        }
        return String.join("；", recs);
    }

    public VideoQualityAnalysisResult manualDetect(Long cameraId, int mockScenario) {
        Camera camera = cameraService.getById(cameraId);
        if (camera == null) {
            throw new BusinessException("摄像头不存在");
        }
        VideoQualityAnalysisResult result = analyzer.analyzeMock(cameraId, camera.getCameraName(), mockScenario);
        saveDetectionResult(result);
        return result;
    }

    public VideoQualityRecord getRecordById(Long id) {
        return recordMapper.selectById(id);
    }

    public VideoHealthDiagnosis getDiagnosisById(Long id) {
        return diagnosisMapper.selectById(id);
    }

    public List<VideoQualityRecord> getRecentRecords(Long cameraId, int limit) {
        LambdaQueryWrapper<VideoQualityRecord> wrapper = new LambdaQueryWrapper<>();
        if (cameraId != null) {
            wrapper.eq(VideoQualityRecord::getCameraId, cameraId);
        }
        wrapper.orderByDesc(VideoQualityRecord::getDetectionTime)
                .last("LIMIT " + limit);
        return recordMapper.selectList(wrapper);
    }

    private static class AbnormalCounter {
        int consecutiveCount;
        final Set<String> abnormalTypes = new HashSet<>();
        long lastAbnormalTime;
    }
}

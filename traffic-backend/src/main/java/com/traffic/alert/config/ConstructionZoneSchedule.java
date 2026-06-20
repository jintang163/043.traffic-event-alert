package com.traffic.alert.config;

import com.traffic.alert.common.BusinessException;
import com.traffic.alert.dto.AiEventCallbackRequest;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.entity.ConstructionPlan;
import com.traffic.alert.service.AlertEventService;
import com.traffic.alert.service.ConstructionPlanService;
import com.traffic.alert.service.ConstructionZoneMonitorService;
import com.traffic.alert.service.ConstructionZoneMonitorService.ConstructionZoneViolation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConstructionZoneSchedule {

    private final ConstructionPlanService constructionPlanService;
    private final ConstructionZoneMonitorService constructionZoneMonitorService;
    private final AlertEventService alertEventService;

    @Scheduled(fixedDelay = 30000, initialDelay = 120000)
    public void checkConstructionZoneViolations() {
        try {
            List<ConstructionPlan> activePlans = constructionPlanService.listActive();
            if (activePlans == null || activePlans.isEmpty()) {
                return;
            }

            log.debug("开始定时检测施工区违规行为, 活跃施工计划数: {}", activePlans.size());

            for (ConstructionPlan plan : activePlans) {
                if (plan.getAlertEnabled() == null || plan.getAlertEnabled() != 1
                        || plan.getCameraId() == null) {
                    continue;
                }

                try {
                    List<ConstructionZoneViolation> violations = constructionZoneMonitorService.detectViolations(plan.getCameraId());
                    if (violations != null && !violations.isEmpty()) {
                        for (ConstructionZoneViolation violation : violations) {
                            createAlertFromViolation(plan, violation);
                        }
                    }
                } catch (Exception e) {
                    log.error("检测施工计划[{}]违规行为失败: {}", plan.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("定时检测施工区违规行为失败: {}", e.getMessage());
        }
    }

    private void createAlertFromViolation(ConstructionPlan plan, ConstructionZoneViolation violation) {
        try {
            AiEventCallbackRequest request = new AiEventCallbackRequest();
            request.setCameraId(plan.getCameraId());
            request.setEventType(violation.getViolationType());
            request.setEventLevel(violation.getSeverity());
            request.setConfidence(BigDecimal.valueOf(0.9).setScale(4, RoundingMode.HALF_UP));
            request.setEventTime(LocalDateTime.now());
            request.setDescription(violation.getDescription() != null
                    ? violation.getDescription()
                    : buildViolationDescription(plan, violation));

            java.util.Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("plan_id", plan.getId());
            metadata.put("plan_code", plan.getPlanCode());
            metadata.put("plan_name", plan.getPlanName());
            metadata.put("speed_limit", violation.getSpeedLimit());
            metadata.put("detected_speed", violation.getSpeed());
            metadata.put("track_id", violation.getTrackId());
            metadata.put("target_class", violation.getTargetClass());
            if (violation.getLongitude() != null) {
                metadata.put("longitude", violation.getLongitude().doubleValue());
            }
            if (violation.getLatitude() != null) {
                metadata.put("latitude", violation.getLatitude().doubleValue());
            }
            request.setMetadata(metadata);

            AlertEvent event = alertEventService.handleAiEventCallback(request);
            log.info("施工区违规行为告警已创建: planId={}, eventNo={}, type={}",
                    plan.getId(), event.getEventNo(), violation.getViolationType());
        } catch (BusinessException e) {
            log.warn("施工区违规行为告警创建被抑制: planId={}, type={}, message={}",
                    plan.getId(), violation.getViolationType(), e.getMessage());
        } catch (Exception e) {
            log.error("创建施工区违规行为告警失败: planId={}, type={}, error={}",
                    plan.getId(), violation.getViolationType(), e.getMessage());
        }
    }

    private String buildViolationDescription(ConstructionPlan plan, ConstructionZoneViolation violation) {
        if ("CONSTRUCTION_INTRUSION".equals(violation.getViolationType())) {
            return String.format("车辆闯入施工区[%s]，限速%.0fkm/h，实测%.1fkm/h",
                    plan.getPlanName(), violation.getSpeedLimit(), violation.getSpeed());
        } else if ("CONSTRUCTION_SPEEDING".equals(violation.getViolationType())) {
            double overSpeedPercent = (violation.getSpeed() / violation.getSpeedLimit() - 1) * 100;
            return String.format("施工区[%s]车辆超速，限速%.0fkm/h，实测%.1fkm/h，超速%.1f%%",
                    plan.getPlanName(), violation.getSpeedLimit(), violation.getSpeed(), overSpeedPercent);
        }
        return String.format("施工区[%s]违规行为: %s", plan.getPlanName(), violation.getViolationType());
    }

    @Scheduled(cron = "0 0 * * * *")
    public void syncActiveConstructionPlansToAiEngine() {
        try {
            List<ConstructionPlan> activePlans = constructionPlanService.listActive();
            if (activePlans == null || activePlans.isEmpty()) {
                return;
            }

            log.info("定时同步活跃施工计划到AI引擎, 计划数: {}", activePlans.size());

            for (ConstructionPlan plan : activePlans) {
                if (plan.getCameraId() != null && plan.getPlanStatus() == 2) {
                    constructionPlanService.syncPlanToAiEngine(plan);
                }
            }
        } catch (Exception e) {
            log.error("定时同步活跃施工计划到AI引擎失败: {}", e.getMessage());
        }
    }
}

package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.ConstructionPlanQuery;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.entity.ConstructionPlan;
import com.traffic.alert.entity.GeoFence;
import com.traffic.alert.mapper.ConstructionPlanMapper;
import com.traffic.alert.utils.GeoPolygonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConstructionPlanService {

    private final ConstructionPlanMapper constructionPlanMapper;
    private final CameraService cameraService;
    private final GeoFenceService geoFenceService;
    private final LedSignService ledSignService;
    @Lazy
    private final AiEngineService aiEngineService;

    private static final DateTimeFormatter PLAN_CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public ConstructionPlan getById(Long id) {
        return constructionPlanMapper.selectById(id);
    }

    public PageResult<ConstructionPlan> page(ConstructionPlanQuery query) {
        Page<ConstructionPlan> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<ConstructionPlan> wrapper = new LambdaQueryWrapper<>();

        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(ConstructionPlan::getPlanName, query.getKeyword())
                    .or().like(ConstructionPlan::getPlanCode, query.getKeyword());
        }
        if (query.getConstructionType() != null) {
            wrapper.eq(ConstructionPlan::getConstructionType, query.getConstructionType());
        }
        if (query.getCameraId() != null) {
            wrapper.eq(ConstructionPlan::getCameraId, query.getCameraId());
        }
        if (query.getPlanStatus() != null) {
            wrapper.eq(ConstructionPlan::getPlanStatus, query.getPlanStatus());
        }
        if (query.getAlertEnabled() != null) {
            wrapper.eq(ConstructionPlan::getAlertEnabled, query.getAlertEnabled());
        }
        if (query.getPlanStartTimeStart() != null) {
            wrapper.ge(ConstructionPlan::getPlanStartTime, query.getPlanStartTimeStart());
        }
        if (query.getPlanStartTimeEnd() != null) {
            wrapper.le(ConstructionPlan::getPlanStartTime, query.getPlanStartTimeEnd());
        }

        wrapper.orderByAsc(ConstructionPlan::getSortOrder).orderByDesc(ConstructionPlan::getCreateTime);
        constructionPlanMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
    }

    public List<ConstructionPlan> listActive() {
        LambdaQueryWrapper<ConstructionPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConstructionPlan::getPlanStatus, 2)
                .eq(ConstructionPlan::getAlertEnabled, 1)
                .orderByAsc(ConstructionPlan::getSortOrder);
        return constructionPlanMapper.selectList(wrapper);
    }

    public List<ConstructionPlan> listByCamera(Long cameraId) {
        LambdaQueryWrapper<ConstructionPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConstructionPlan::getCameraId, cameraId)
                .eq(ConstructionPlan::getPlanStatus, 2)
                .eq(ConstructionPlan::getAlertEnabled, 1)
                .orderByAsc(ConstructionPlan::getSortOrder);
        return constructionPlanMapper.selectList(wrapper);
    }

    @Transactional
    public ConstructionPlan save(ConstructionPlan plan) {
        if (plan.getCameraId() != null) {
            Camera camera = cameraService.getById(plan.getCameraId());
            if (camera != null) {
                plan.setCameraName(camera.getCameraName());
            }
        }

        setTypeLabel(plan);
        setStatusLabel(plan);
        calculatePolygonInfo(plan);

        if (plan.getId() == null) {
            String planCode = plan.getPlanCode();
            if (planCode == null || planCode.isEmpty()) {
                planCode = "CONS" + LocalDateTime.now().format(PLAN_CODE_FORMATTER) +
                        String.format("%04d", (int) (Math.random() * 10000));
                plan.setPlanCode(planCode);
            }
            if (plan.getPlanStatus() == null) {
                plan.setPlanStatus(0);
            }
            if (plan.getSortOrder() == null) {
                plan.setSortOrder(0);
            }
            if (plan.getAlertEnabled() == null) {
                plan.setAlertEnabled(1);
            }
            if (plan.getAlertLevel() == null) {
                plan.setAlertLevel(2);
            }
            if (plan.getSpeedLimit() == null) {
                plan.setSpeedLimit(new BigDecimal("60.00"));
            }
            if (plan.getBufferDistance() == null) {
                plan.setBufferDistance(new BigDecimal("50.00"));
            }
            if (plan.getEventCount() == null) {
                plan.setEventCount(0);
            }
            if (plan.getConeAlertCount() == null) {
                plan.setConeAlertCount(0);
            }
            if (plan.getIntrusionAlertCount() == null) {
                plan.setIntrusionAlertCount(0);
            }
            if (plan.getSpeedingAlertCount() == null) {
                plan.setSpeedingAlertCount(0);
            }
            setStatusLabel(plan);
            constructionPlanMapper.insert(plan);
            log.info("创建施工计划: planCode={}, planName={}", plan.getPlanCode(), plan.getPlanName());
        } else {
            ConstructionPlan exist = getById(plan.getId());
            if (exist == null) {
                throw new BusinessException("施工计划不存在");
            }
            constructionPlanMapper.updateById(plan);
            log.info("更新施工计划: planId={}, planName={}", plan.getId(), plan.getPlanName());
        }

        return plan;
    }

    @Transactional
    public void delete(Long id) {
        ConstructionPlan exist = getById(id);
        if (exist == null) {
            throw new BusinessException("施工计划不存在");
        }
        constructionPlanMapper.deleteById(id);
        log.info("删除施工计划: planId={}, planName={}", id, exist.getPlanName());
    }

    @Transactional
    public ConstructionPlan updateStatus(Long id, int status) {
        ConstructionPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("施工计划不存在");
        }
        plan.setPlanStatus(status);
        setStatusLabel(plan);

        if (status == 2 && plan.getActualStartTime() == null) {
            plan.setActualStartTime(LocalDateTime.now());
            enableConstructionFences(plan);
            if (plan.getLedReminderEnabled() != null && plan.getLedReminderEnabled() == 1) {
                triggerLedReminder(plan);
            }
            syncPlanToAiEngine(plan);
        } else if (status == 3 && plan.getActualEndTime() == null) {
            plan.setActualEndTime(LocalDateTime.now());
            disableConstructionFences(plan);
            if (plan.getCameraId() != null) {
                aiEngineService.removeConstructionPlan(plan.getCameraId());
            }
        }

        if (status == 4 && plan.getCameraId() != null) {
            aiEngineService.removeConstructionPlan(plan.getCameraId());
        }

        constructionPlanMapper.updateById(plan);
        log.info("更新施工计划状态: planId={}, status={}", id, status);
        return plan;
    }

    @Transactional
    public ConstructionPlan toggleAlert(Long id, boolean enabled) {
        ConstructionPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("施工计划不存在");
        }
        plan.setAlertEnabled(enabled ? 1 : 0);
        constructionPlanMapper.updateById(plan);
        log.info("{}施工计划告警: planId={}", enabled ? "启用" : "禁用", id);
        return plan;
    }

    private void enableConstructionFences(ConstructionPlan plan) {
        if (plan.getFenceId() != null) {
            try {
                geoFenceService.toggleStatus(plan.getFenceId(), 1);
                geoFenceService.toggleAlert(plan.getFenceId(), true);
                log.info("启用施工区围栏: fenceId={}", plan.getFenceId());
            } catch (Exception e) {
                log.warn("启用施工区围栏失败: fenceId={}, error={}", plan.getFenceId(), e.getMessage());
            }
        }
        if (plan.getBufferFenceId() != null) {
            try {
                geoFenceService.toggleStatus(plan.getBufferFenceId(), 1);
                geoFenceService.toggleAlert(plan.getBufferFenceId(), true);
                log.info("启用缓冲区围栏: fenceId={}", plan.getBufferFenceId());
            } catch (Exception e) {
                log.warn("启用缓冲区围栏失败: fenceId={}, error={}", plan.getBufferFenceId(), e.getMessage());
            }
        }
    }

    private void disableConstructionFences(ConstructionPlan plan) {
        if (plan.getFenceId() != null) {
            try {
                geoFenceService.toggleStatus(plan.getFenceId(), 0);
                log.info("禁用施工区围栏: fenceId={}", plan.getFenceId());
            } catch (Exception e) {
                log.warn("禁用施工区围栏失败: fenceId={}, error={}", plan.getFenceId(), e.getMessage());
            }
        }
        if (plan.getBufferFenceId() != null) {
            try {
                geoFenceService.toggleStatus(plan.getBufferFenceId(), 0);
                log.info("禁用缓冲区围栏: fenceId={}", plan.getBufferFenceId());
            } catch (Exception e) {
                log.warn("禁用缓冲区围栏失败: fenceId={}, error={}", plan.getBufferFenceId(), e.getMessage());
            }
        }
    }

    private void triggerLedReminder(ConstructionPlan plan) {
        if (plan.getLedReminderEnabled() != null && plan.getLedReminderEnabled() == 1
                && plan.getCameraId() != null) {
            try {
                String message = plan.getLedDefaultMessage() != null ?
                        plan.getLedDefaultMessage() : "前方施工 减速慢行";
                ledSignService.displayMessage(plan.getCameraId(), message, "YELLOW", true, 60);
                log.info("触发LED施工提醒: cameraId={}, message={}", plan.getCameraId(), message);
            } catch (Exception e) {
                log.warn("触发LED提醒失败: cameraId={}, error={}", plan.getCameraId(), e.getMessage());
            }
        }
    }

    public void syncPlanToAiEngine(ConstructionPlan plan) {
        if (plan.getCameraId() == null) {
            return;
        }
        try {
            Map<String, Object> planConfig = new java.util.HashMap<>();
            planConfig.put("id", plan.getId());
            planConfig.put("plan_code", plan.getPlanCode());
            planConfig.put("plan_name", plan.getPlanName());
            planConfig.put("construction_type", plan.getConstructionType());
            planConfig.put("plan_status", plan.getPlanStatus());
            planConfig.put("camera_id", plan.getCameraId());
            planConfig.put("fence_id", plan.getFenceId());
            planConfig.put("buffer_fence_id", plan.getBufferFenceId());
            planConfig.put("speed_limit", plan.getSpeedLimit() != null ? plan.getSpeedLimit().doubleValue() : 60.0);
            planConfig.put("standard_cone_count", plan.getStandardConeCount() != null ? plan.getStandardConeCount() : 0);
            planConfig.put("buffer_distance", plan.getBufferDistance() != null ? plan.getBufferDistance().doubleValue() : 50.0);
            planConfig.put("alert_enabled", plan.getAlertEnabled());
            planConfig.put("alert_level", plan.getAlertLevel());
            planConfig.put("polygon_points", plan.getPolygonPoints());
            planConfig.put("polygon_points_pixel", plan.getPolygonPointsPixel());

            if (plan.getBufferFenceId() != null) {
                GeoFence bufferFence = geoFenceService.getById(plan.getBufferFenceId());
                if (bufferFence != null) {
                    planConfig.put("buffer_polygon_points", bufferFence.getPolygonPoints());
                    planConfig.put("buffer_polygon_points_pixel", bufferFence.getPolygonPointsPixel());
                }
            }

            aiEngineService.syncConstructionPlan(plan.getCameraId(), planConfig);
            log.info("同步施工计划配置到AI引擎: planId={}, cameraId={}", plan.getId(), plan.getCameraId());
        } catch (Exception e) {
            log.error("同步施工计划配置到AI引擎失败: planId={}, error={}", plan.getId(), e.getMessage());
        }
    }

    public void incrementEventCount(Long planId, String eventType) {
        ConstructionPlan plan = getById(planId);
        if (plan == null) {
            return;
        }
        plan.setEventCount((plan.getEventCount() != null ? plan.getEventCount() : 0) + 1);

        if ("CONE_MISSING".equals(eventType)) {
            plan.setConeAlertCount((plan.getConeAlertCount() != null ? plan.getConeAlertCount() : 0) + 1);
        } else if ("INTRUSION".equals(eventType)) {
            plan.setIntrusionAlertCount((plan.getIntrusionAlertCount() != null ? plan.getIntrusionAlertCount() : 0) + 1);
        } else if ("SPEEDING".equals(eventType)) {
            plan.setSpeedingAlertCount((plan.getSpeedingAlertCount() != null ? plan.getSpeedingAlertCount() : 0) + 1);
        }

        constructionPlanMapper.updateById(plan);
    }

    private void setTypeLabel(ConstructionPlan plan) {
        if (plan.getConstructionType() == null) return;
        String label = switch (plan.getConstructionType()) {
            case 1 -> "日常养护";
            case 2 -> "道路维修";
            case 3 -> "应急抢修";
            case 4 -> "改扩建";
            default -> "其他";
        };
        plan.setConstructionTypeLabel(label);
    }

    private void setStatusLabel(ConstructionPlan plan) {
        if (plan.getPlanStatus() == null) return;
        String label = switch (plan.getPlanStatus()) {
            case 0 -> "草稿";
            case 1 -> "待执行";
            case 2 -> "进行中";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "未知";
        };
        plan.setPlanStatusLabel(label);
    }

    private void calculatePolygonInfo(ConstructionPlan plan) {
        if (plan.getPolygonPoints() == null || plan.getPolygonPoints().isEmpty()) {
            return;
        }

        List<GeoPolygonUtils.Point> polygon = GeoPolygonUtils.parsePolygonPoints(plan.getPolygonPoints());
        if (polygon.size() >= 3) {
            double area = GeoPolygonUtils.calculatePolygonAreaSquareMeters(polygon);
            GeoPolygonUtils.Point center = GeoPolygonUtils.getPolygonCenter(polygon);
            plan.setCenterLongitude(BigDecimal.valueOf(center.lng).setScale(6, RoundingMode.HALF_UP));
            plan.setCenterLatitude(BigDecimal.valueOf(center.lat).setScale(6, RoundingMode.HALF_UP));
        }
    }

    public List<ConstructionPlan> getActivePlansForCamera(Long cameraId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<ConstructionPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConstructionPlan::getCameraId, cameraId)
                .eq(ConstructionPlan::getPlanStatus, 2)
                .eq(ConstructionPlan::getAlertEnabled, 1)
                .le(ConstructionPlan::getPlanStartTime, now)
                .ge(ConstructionPlan::getPlanEndTime, now)
                .orderByAsc(ConstructionPlan::getSortOrder);
        return constructionPlanMapper.selectList(wrapper);
    }

    public boolean checkSpeeding(Long planId, double speed) {
        ConstructionPlan plan = getById(planId);
        if (plan == null || plan.getSpeedLimit() == null) {
            return false;
        }
        return speed > plan.getSpeedLimit().doubleValue();
    }
}

package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.GeoFenceQuery;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.entity.GeoFence;
import com.traffic.alert.mapper.GeoFenceMapper;
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
import java.util.Map;

@Slf4j
@Service
public class GeoFenceService {

    private final GeoFenceMapper geoFenceMapper;
    private final CameraService cameraService;
    private final AiEngineService aiEngineService;

    public GeoFenceService(GeoFenceMapper geoFenceMapper,
                           @Lazy CameraService cameraService,
                           AiEngineService aiEngineService) {
        this.geoFenceMapper = geoFenceMapper;
        this.cameraService = cameraService;
        this.aiEngineService = aiEngineService;
    }

    private static final DateTimeFormatter FENCE_CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public GeoFence getById(Long id) {
        return geoFenceMapper.selectById(id);
    }

    public PageResult<GeoFence> page(GeoFenceQuery query) {
        Page<GeoFence> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<GeoFence> wrapper = new LambdaQueryWrapper<>();

        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(GeoFence::getFenceName, query.getKeyword())
                    .or().like(GeoFence::getFenceCode, query.getKeyword());
        }
        if (query.getFenceType() != null) {
            wrapper.eq(GeoFence::getFenceType, query.getFenceType());
        }
        if (query.getCameraId() != null) {
            wrapper.eq(GeoFence::getCameraId, query.getCameraId());
        }
        if (query.getAlertEnabled() != null) {
            wrapper.eq(GeoFence::getAlertEnabled, query.getAlertEnabled());
        }
        if (query.getStatus() != null) {
            wrapper.eq(GeoFence::getStatus, query.getStatus());
        }

        wrapper.orderByAsc(GeoFence::getSortOrder).orderByDesc(GeoFence::getCreateTime);
        geoFenceMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
    }

    public List<GeoFence> listByCamera(Long cameraId) {
        LambdaQueryWrapper<GeoFence> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GeoFence::getCameraId, cameraId)
                .eq(GeoFence::getStatus, 1)
                .eq(GeoFence::getAlertEnabled, 1)
                .orderByAsc(GeoFence::getSortOrder);
        return geoFenceMapper.selectList(wrapper);
    }

    public List<GeoFence> listAllEnabled() {
        LambdaQueryWrapper<GeoFence> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GeoFence::getStatus, 1)
                .eq(GeoFence::getAlertEnabled, 1)
                .orderByAsc(GeoFence::getSortOrder);
        return geoFenceMapper.selectList(wrapper);
    }

    @Transactional
    public GeoFence save(GeoFence geoFence) {
        if (geoFence.getCameraId() != null) {
            Camera camera = cameraService.getById(geoFence.getCameraId());
            if (camera != null) {
                geoFence.setCameraName(camera.getCameraName());
            }
        }

        calculatePolygonInfo(geoFence);

        if (geoFence.getId() == null) {
            String fenceCode = geoFence.getFenceCode();
            if (fenceCode == null || fenceCode.isEmpty()) {
                fenceCode = "FENCE" + LocalDateTime.now().format(FENCE_CODE_FORMATTER) +
                        String.format("%04d", (int) (Math.random() * 10000));
                geoFence.setFenceCode(fenceCode);
            }
            if (geoFence.getStatus() == null) {
                geoFence.setStatus(1);
            }
            if (geoFence.getSortOrder() == null) {
                geoFence.setSortOrder(0);
            }
            if (geoFence.getColor() == null || geoFence.getColor().isEmpty()) {
                geoFence.setColor(getDefaultColor(geoFence.getFenceType()));
            }
            geoFenceMapper.insert(geoFence);
            log.info("创建电子围栏: fenceCode={}, fenceName={}", geoFence.getFenceCode(), geoFence.getFenceName());
            syncFenceToEngine("add", geoFence);
        } else {
            GeoFence exist = getById(geoFence.getId());
            if (exist == null) {
                throw new BusinessException("围栏不存在");
            }
            geoFenceMapper.updateById(geoFence);
            log.info("更新电子围栏: fenceId={}, fenceName={}", geoFence.getId(), geoFence.getFenceName());
            syncFenceToEngine("update", geoFence);
        }

        return geoFence;
    }

    @Transactional
    public void delete(Long id) {
        GeoFence exist = getById(id);
        if (exist == null) {
            throw new BusinessException("围栏不存在");
        }
        geoFenceMapper.deleteById(id);
        log.info("删除电子围栏: fenceId={}, fenceName={}", id, exist.getFenceName());
        syncFenceToEngine("delete", exist);
    }

    public GeoFence toggleAlert(Long id, boolean enabled) {
        GeoFence fence = getById(id);
        if (fence == null) {
            throw new BusinessException("围栏不存在");
        }
        fence.setAlertEnabled(enabled ? 1 : 0);
        geoFenceMapper.updateById(fence);
        log.info("{}围栏告警: fenceId={}", enabled ? "启用" : "禁用", id);
        syncFenceToEngine("update", fence);
        return fence;
    }

    public GeoFence toggleStatus(Long id, int status) {
        GeoFence fence = getById(id);
        if (fence == null) {
            throw new BusinessException("围栏不存在");
        }
        fence.setStatus(status);
        geoFenceMapper.updateById(fence);
        log.info("设置围栏状态: fenceId={}, status={}", id, status);
        syncFenceToEngine("update", fence);
        return fence;
    }

    public boolean checkPointInFence(Long fenceId, double lng, double lat) {
        GeoFence fence = getById(fenceId);
        if (fence == null || fence.getPolygonPoints() == null) {
            return false;
        }
        List<GeoPolygonUtils.Point> polygon = GeoPolygonUtils.parsePolygonPoints(fence.getPolygonPoints());
        return GeoPolygonUtils.isPointInPolygon(new GeoPolygonUtils.Point(lng, lat), polygon);
    }

    public List<GeoFence> findFencesContainingPoint(double lng, double lat, Long cameraId) {
        List<GeoFence> fences = cameraId != null ? listByCamera(cameraId) : listAllEnabled();
        return fences.stream()
                .filter(f -> f.getPolygonPoints() != null && !f.getPolygonPoints().isEmpty())
                .filter(f -> {
                    List<GeoPolygonUtils.Point> polygon = GeoPolygonUtils.parsePolygonPoints(f.getPolygonPoints());
                    return GeoPolygonUtils.isPointInPolygon(new GeoPolygonUtils.Point(lng, lat), polygon);
                })
                .toList();
    }

    private void calculatePolygonInfo(GeoFence geoFence) {
        if (geoFence.getPolygonPoints() == null || geoFence.getPolygonPoints().isEmpty()) {
            return;
        }

        List<GeoPolygonUtils.Point> polygon = GeoPolygonUtils.parsePolygonPoints(geoFence.getPolygonPoints());
        if (polygon.size() >= 3) {
            double area = GeoPolygonUtils.calculatePolygonAreaSquareMeters(polygon);
            geoFence.setArea(BigDecimal.valueOf(area).setScale(2, RoundingMode.HALF_UP));

            GeoPolygonUtils.Point center = GeoPolygonUtils.getPolygonCenter(polygon);
            geoFence.setCenterLongitude(BigDecimal.valueOf(center.lng).setScale(6, RoundingMode.HALF_UP));
            geoFence.setCenterLatitude(BigDecimal.valueOf(center.lat).setScale(6, RoundingMode.HALF_UP));
        }
    }

    private String getDefaultColor(Integer fenceType) {
        if (fenceType == null) {
            return "#ff4d4f";
        }
        return switch (fenceType) {
            case 1 -> "#faad14";
            case 2 -> "#ff4d4f";
            case 3 -> "#52c41a";
            default -> "#1890ff";
        };
    }

    private void syncFenceToEngine(String action, GeoFence fence) {
        try {
            Map<String, Object> fenceData = Map.ofEntries(
                    Map.entry("fenceId", String.valueOf(fence.getId())),
                    Map.entry("fenceCode", fence.getFenceCode() != null ? fence.getFenceCode() : ""),
                    Map.entry("fenceName", fence.getFenceName() != null ? fence.getFenceName() : ""),
                    Map.entry("fenceType", fence.getFenceType() != null ? fence.getFenceType() : 1),
                    Map.entry("cameraId", fence.getCameraId() != null ? fence.getCameraId() : 0),
                    Map.entry("polygonPointsPixel", fence.getPolygonPointsPixel() != null ? fence.getPolygonPointsPixel() : "[]"),
                    Map.entry("polygonPoints", fence.getPolygonPoints() != null ? fence.getPolygonPoints() : "[]"),
                    Map.entry("alertEnabled", fence.getAlertEnabled() != null ? fence.getAlertEnabled() : 1),
                    Map.entry("alertLevel", fence.getAlertLevel() != null ? fence.getAlertLevel() : 2),
                    Map.entry("detectTargetTypes", fence.getDetectTargetTypes() != null ? fence.getDetectTargetTypes() : ""),
                    Map.entry("staySeconds", fence.getStaySeconds() != null ? fence.getStaySeconds() : 0),
                    Map.entry("cooldownSeconds", fence.getCooldownSeconds() != null ? fence.getCooldownSeconds() : 60),
                    Map.entry("notifyEnabled", fence.getNotifyEnabled() != null ? fence.getNotifyEnabled() : 1),
                    Map.entry("linkWorkOrder", fence.getLinkWorkOrder() != null ? fence.getLinkWorkOrder() : 0),
                    Map.entry("color", fence.getColor() != null ? fence.getColor() : "#ff4d4f"),
                    Map.entry("description", fence.getDescription() != null ? fence.getDescription() : "")
            );
            boolean success = aiEngineService.syncFenceToEngine(action, fenceData);
            if (!success) {
                log.warn("同步围栏到AI引擎未成功: action={}, fenceId={}", action, fence.getId());
            }
        } catch (Exception e) {
            log.error("同步围栏到AI引擎异常: action={}, fenceId={}, error={}", action, fence.getId(), e.getMessage());
        }
    }
}

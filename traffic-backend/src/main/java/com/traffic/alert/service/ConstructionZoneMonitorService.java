package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.entity.ConstructionPlan;
import com.traffic.alert.entity.GeoFence;
import com.traffic.alert.entity.TrackPoint;
import com.traffic.alert.mapper.TrackPointMapper;
import com.traffic.alert.utils.GeoPolygonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConstructionZoneMonitorService {

    private final ConstructionPlanService constructionPlanService;
    private final GeoFenceService geoFenceService;
    private final GlobalTrackService globalTrackService;
    private final TrackPointMapper trackPointMapper;

    public List<ConstructionPlan> getActivePlansForCamera(Long cameraId) {
        return constructionPlanService.getActivePlansForCamera(cameraId);
    }

    public boolean checkPointInConstructionZone(Long planId, double lng, double lat) {
        ConstructionPlan plan = constructionPlanService.getById(planId);
        if (plan == null || plan.getPolygonPoints() == null) {
            return false;
        }
        List<GeoPolygonUtils.Point> polygon = GeoPolygonUtils.parsePolygonPoints(plan.getPolygonPoints());
        return GeoPolygonUtils.isPointInPolygon(new GeoPolygonUtils.Point(lng, lat), polygon);
    }

    public boolean checkSpeeding(Long planId, double speedKmH) {
        return constructionPlanService.checkSpeeding(planId, speedKmH);
    }

    public List<ConstructionZoneViolation> detectViolations(Long cameraId) {
        List<ConstructionZoneViolation> violations = new ArrayList<>();
        List<ConstructionPlan> plans = getActivePlansForCamera(cameraId);

        if (plans.isEmpty()) {
            return violations;
        }

        LambdaQueryWrapper<TrackPoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrackPoint::getCameraId, cameraId)
                .gt(TrackPoint::getCreateTime, LocalDateTime.now().minusMinutes(5))
                .orderByDesc(TrackPoint::getCreateTime)
                .last("LIMIT 100");
        List<TrackPoint> trackPoints = trackPointMapper.selectList(wrapper);

        if (trackPoints == null || trackPoints.isEmpty()) {
            return violations;
        }

        for (ConstructionPlan plan : plans) {
            if (plan.getAlertEnabled() == null || plan.getAlertEnabled() != 1) {
                continue;
            }

            List<GeoPolygonUtils.Point> zonePolygon = null;
            if (plan.getFenceId() != null) {
                GeoFence fence = geoFenceService.getById(plan.getFenceId());
                if (fence != null && fence.getPolygonPoints() != null) {
                    zonePolygon = GeoPolygonUtils.parsePolygonPoints(fence.getPolygonPoints());
                }
            } else if (plan.getPolygonPoints() != null && !plan.getPolygonPoints().isEmpty()) {
                zonePolygon = GeoPolygonUtils.parsePolygonPoints(plan.getPolygonPoints());
            }

            List<GeoPolygonUtils.Point> bufferPolygon = null;
            if (plan.getBufferFenceId() != null) {
                GeoFence bufferFence = geoFenceService.getById(plan.getBufferFenceId());
                if (bufferFence != null && bufferFence.getPolygonPoints() != null) {
                    bufferPolygon = GeoPolygonUtils.parsePolygonPoints(bufferFence.getPolygonPoints());
                }
            }

            double speedLimit = plan.getSpeedLimit() != null ? plan.getSpeedLimit().doubleValue() : 60.0;

            for (TrackPoint track : trackPoints) {
                if (track.getLongitude() == null || track.getLatitude() == null) {
                    continue;
                }

                GeoPolygonUtils.Point point = new GeoPolygonUtils.Point(
                        track.getLongitude().doubleValue(),
                        track.getLatitude().doubleValue()
                );

                boolean inZone = zonePolygon != null && GeoPolygonUtils.isPointInPolygon(point, zonePolygon);
                boolean inBuffer = bufferPolygon != null && GeoPolygonUtils.isPointInPolygon(point, bufferPolygon);

                double speed = track.getSpeed() != null ? track.getSpeed().doubleValue() : 0.0;
                boolean isSpeeding = speed > speedLimit;

                if (inZone) {
                    ConstructionZoneViolation violation = new ConstructionZoneViolation();
                    violation.setViolationType("CONSTRUCTION_INTRUSION");
                    violation.setViolationTypeLabel("闯入施工区");
                    violation.setPlanId(plan.getId());
                    violation.setPlanName(plan.getPlanName());
                    violation.setTrackId(track.getTrackId());
                    violation.setTargetClass(track.getTargetClass());
                    violation.setSpeed(speed);
                    violation.setSpeedLimit(speedLimit);
                    violation.setLongitude(track.getLongitude());
                    violation.setLatitude(track.getLatitude());
                    violation.setSeverity(isSpeeding ? 3 : 2);
                    violations.add(violation);
                } else if (inBuffer && isSpeeding) {
                    ConstructionZoneViolation violation = new ConstructionZoneViolation();
                    violation.setViolationType("CONSTRUCTION_SPEEDING");
                    violation.setViolationTypeLabel("施工区超速");
                    violation.setPlanId(plan.getId());
                    violation.setPlanName(plan.getPlanName());
                    violation.setTrackId(track.getTrackId());
                    violation.setTargetClass(track.getTargetClass());
                    violation.setSpeed(speed);
                    violation.setSpeedLimit(speedLimit);
                    violation.setLongitude(track.getLongitude());
                    violation.setLatitude(track.getLatitude());
                    violation.setSeverity(2);
                    violations.add(violation);
                }
            }
        }

        return violations;
    }

    public void processEventForConstructionPlans(AlertEvent event) {
        if (event.getCameraId() == null) {
            return;
        }

        List<ConstructionPlan> plans = getActivePlansForCamera(event.getCameraId());
        if (plans.isEmpty()) {
            return;
        }

        for (ConstructionPlan plan : plans) {
            boolean isRelated = isEventRelatedToPlan(event, plan);
            if (isRelated) {
                constructionPlanService.incrementEventCount(plan.getId(), event.getEventType());
                log.info("事件关联到施工计划: eventNo={}, planId={}, planName={}, eventType={}",
                        event.getEventNo(), plan.getId(), plan.getPlanName(), event.getEventType());
            }
        }
    }

    private boolean isEventRelatedToPlan(AlertEvent event, ConstructionPlan plan) {
        if (event.getLongitude() == null || event.getLatitude() == null) {
            return true;
        }

        if (plan.getPolygonPoints() != null && !plan.getPolygonPoints().isEmpty()) {
            List<GeoPolygonUtils.Point> polygon = GeoPolygonUtils.parsePolygonPoints(plan.getPolygonPoints());
            if (GeoPolygonUtils.isPointInPolygon(
                    new GeoPolygonUtils.Point(event.getLongitude().doubleValue(), event.getLatitude().doubleValue()),
                    polygon)) {
                return true;
            }
        }

        if (plan.getBufferFenceId() != null) {
            GeoFence bufferFence = geoFenceService.getById(plan.getBufferFenceId());
            if (bufferFence != null && bufferFence.getPolygonPoints() != null) {
                List<GeoPolygonUtils.Point> bufferPolygon = GeoPolygonUtils.parsePolygonPoints(bufferFence.getPolygonPoints());
                if (GeoPolygonUtils.isPointInPolygon(
                        new GeoPolygonUtils.Point(event.getLongitude().doubleValue(), event.getLatitude().doubleValue()),
                        bufferPolygon)) {
                    return true;
                }
            }
        }

        return false;
    }

    public List<GeoFence> getConstructionZoneFences(Long planId) {
        List<GeoFence> fences = new ArrayList<>();
        ConstructionPlan plan = constructionPlanService.getById(planId);
        if (plan == null) {
            return fences;
        }

        if (plan.getFenceId() != null) {
            GeoFence fence = geoFenceService.getById(plan.getFenceId());
            if (fence != null) {
                fences.add(fence);
            }
        }

        if (plan.getBufferFenceId() != null) {
            GeoFence bufferFence = geoFenceService.getById(plan.getBufferFenceId());
            if (bufferFence != null) {
                fences.add(bufferFence);
            }
        }

        return fences;
    }

    public BigDecimal calculateBufferDistance(Long planId) {
        ConstructionPlan plan = constructionPlanService.getById(planId);
        if (plan == null) {
            return BigDecimal.ZERO;
        }
        return plan.getBufferDistance() != null ? plan.getBufferDistance() : BigDecimal.valueOf(50.0);
    }

    public static class ConstructionZoneViolation {
        private String violationType;
        private String violationTypeLabel;
        private Long planId;
        private String planName;
        private Long trackId;
        private String targetClass;
        private double speed;
        private double speedLimit;
        private String location;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private int severity;

        public String getViolationType() { return violationType; }
        public void setViolationType(String violationType) { this.violationType = violationType; }
        public String getViolationTypeLabel() { return violationTypeLabel; }
        public void setViolationTypeLabel(String violationTypeLabel) { this.violationTypeLabel = violationTypeLabel; }
        public Long getPlanId() { return planId; }
        public void setPlanId(Long planId) { this.planId = planId; }
        public String getPlanName() { return planName; }
        public void setPlanName(String planName) { this.planName = planName; }
        public Long getTrackId() { return trackId; }
        public void setTrackId(Long trackId) { this.trackId = trackId; }
        public String getTargetClass() { return targetClass; }
        public void setTargetClass(String targetClass) { this.targetClass = targetClass; }
        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }
        public double getSpeedLimit() { return speedLimit; }
        public void setSpeedLimit(double speedLimit) { this.speedLimit = speedLimit; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public BigDecimal getLongitude() { return longitude; }
        public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
        public BigDecimal getLatitude() { return latitude; }
        public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
        public int getSeverity() { return severity; }
        public void setSeverity(int severity) { this.severity = severity; }
    }
}

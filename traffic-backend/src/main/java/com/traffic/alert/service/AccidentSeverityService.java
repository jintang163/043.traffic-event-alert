package com.traffic.alert.service;

import com.traffic.alert.dto.AiEventCallbackRequest;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.enums.AccidentSeverity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AccidentSeverityService {

    private static final int DEFORMATION_SCORE_NONE = 0;
    private static final int DEFORMATION_SCORE_MINOR = 1;
    private static final int DEFORMATION_SCORE_MODERATE = 2;
    private static final int DEFORMATION_SCORE_SEVERE = 3;
    private static final int DEFORMATION_SCORE_TOTAL = 4;

    private static final int THRESHOLD_MAJOR = 12;
    private static final int THRESHOLD_GENERAL = 5;

    public AccidentSeverity evaluate(AlertEvent event, AiEventCallbackRequest request) {
        if (!"ACCIDENT".equals(event.getEventType())) {
            return null;
        }

        setAccidentFeatures(event, request);

        if (request != null && request.getAccidentSeverity() != null
                && !request.getAccidentSeverity().isEmpty()) {
            AccidentSeverity override = AccidentSeverity.of(request.getAccidentSeverity());
            List<String> reasons = new ArrayList<>();
            reasons.add("AI直接指定等级:" + override.getLabel());
            applySeverity(event, override, reasons);
            log.info("事故等级由AI直接指定: eventNo={}, severity={}({})",
                    event.getEventNo(), override.getCode(), override.getLabel());
            return override;
        }

        boolean hasExplicitFeatures = hasAnyFeature(event);

        if (!hasExplicitFeatures && request != null) {
            inferFeaturesFromCallback(event, request);
        }

        int totalScore = calculateScore(event);
        AccidentSeverity severity = resolveSeverity(totalScore);

        if (!hasExplicitFeatures && !hasInferredFeatures(event) && totalScore == 0) {
            severity = AccidentSeverity.GENERAL;
        }

        List<String> reasons = buildReasons(event, totalScore, severity, hasExplicitFeatures || hasInferredFeatures(event));

        applySeverity(event, severity, reasons);

        log.info("事故严重程度评估完成: eventNo={}, score={}, severity={}({}), reasons={}",
                event.getEventNo(), totalScore, severity.getCode(), severity.getLabel(), reasons);

        return severity;
    }

    private boolean hasAnyFeature(AlertEvent event) {
        return (event.getAccidentFire() != null && event.getAccidentFire() == 1)
                || (event.getAccidentRollover() != null && event.getAccidentRollover() == 1)
                || (event.getAccidentCasualty() != null && event.getAccidentCasualty() > 0)
                || (event.getAccidentDeformationLevel() != null && event.getAccidentDeformationLevel() > 0)
                || (event.getAccidentVehicles() != null && event.getAccidentVehicles() > 1)
                || (event.getAccidentImpactSpeed() != null && event.getAccidentImpactSpeed().compareTo(BigDecimal.ZERO) > 0);
    }

    private boolean hasInferredFeatures(AlertEvent event) {
        return (event.getAccidentVehicles() != null && event.getAccidentVehicles() > 1)
                || (event.getAccidentDeformationLevel() != null && event.getAccidentDeformationLevel() > 0)
                || (event.getAccidentImpactSpeed() != null && event.getAccidentImpactSpeed().compareTo(BigDecimal.ZERO) > 0);
    }

    private void inferFeaturesFromCallback(AlertEvent event, AiEventCallbackRequest request) {
        List<Map<String, Object>> trackData = request.getTrackData();
        Map<String, Object> bbox = request.getBbox();

        if (trackData != null && !trackData.isEmpty()) {
            Map<String, List<Map<String, Object>>> groupedByClass = new HashMap<>();
            for (Map<String, Object> track : trackData) {
                String className = track.get("className") != null ? track.get("className").toString() : "unknown";
                groupedByClass.computeIfAbsent(className, k -> new ArrayList<>()).add(track);
            }

            int vehicleCount = 0;
            for (Map.Entry<String, List<Map<String, Object>>> entry : groupedByClass.entrySet()) {
                String cls = entry.getKey().toLowerCase();
                if (cls.contains("car") || cls.contains("truck") || cls.contains("bus")
                        || cls.contains("vehicle") || cls.contains("van") || cls.contains("motorcycle")) {
                    vehicleCount += entry.getValue().size();
                }
            }

            if (vehicleCount > event.getAccidentVehicles()) {
                event.setAccidentVehicles(vehicleCount);
                log.debug("兜底推断: 涉事车辆数={}", vehicleCount);
            }

            if (vehicleCount >= 3) {
                boolean hasLargeOverlap = checkBboxOverlap(trackData);
                if (hasLargeOverlap) {
                    event.setAccidentDeformationLevel(DEFORMATION_SCORE_MODERATE);
                    log.debug("兜底推断: 多车碰撞Bbox重叠→中度变形");
                }
            }

            boolean hasHighSpeed = checkTrackSpeeds(trackData);
            if (hasHighSpeed) {
                event.setAccidentImpactSpeed(BigDecimal.valueOf(60));
                log.debug("兜底推断: 检测到高速运动→碰撞车速≥60km/h");
            }
        }

        if (bbox != null) {
            Object classNameObj = bbox.get("className");
            if (classNameObj != null) {
                String cls = classNameObj.toString().toLowerCase();
                if (cls.contains("fire") || cls.contains("flame") || cls.contains("smoke")) {
                    event.setAccidentFire(1);
                    log.debug("兜底推断: 检测框类型含火灾/烟雾→起火");
                }
                if (cls.contains("rollover") || cls.contains("overturned") || cls.contains("flipped")) {
                    event.setAccidentRollover(1);
                    log.debug("兜底推断: 检测框类型含翻滚→翻车");
                }
            }

            Object confidenceObj = bbox.get("accidentDeformationLevel");
            if (confidenceObj instanceof Number) {
                int lv = ((Number) confidenceObj).intValue();
                if (lv >= DEFORMATION_SCORE_MINOR && lv <= DEFORMATION_SCORE_TOTAL) {
                    event.setAccidentDeformationLevel(lv);
                }
            }
            Object fireObj = bbox.get("accidentFire");
            if (fireObj instanceof Number && ((Number) fireObj).intValue() == 1) {
                event.setAccidentFire(1);
            }
            Object rolloverObj = bbox.get("accidentRollover");
            if (rolloverObj instanceof Number && ((Number) rolloverObj).intValue() == 1) {
                event.setAccidentRollover(1);
            }
            Object speedObj = bbox.get("accidentImpactSpeed");
            if (speedObj instanceof Number) {
                event.setAccidentImpactSpeed(BigDecimal.valueOf(((Number) speedObj).doubleValue()));
            }
            Object casualtyObj = bbox.get("accidentCasualty");
            if (casualtyObj instanceof Number) {
                event.setAccidentCasualty(((Number) casualtyObj).intValue());
            }
        }

        if (request.getDescription() != null) {
            String desc = request.getDescription().toLowerCase();
            if (desc.contains("起火") || desc.contains("着火") || desc.contains("燃烧") || desc.contains("fire")) {
                event.setAccidentFire(1);
                log.debug("兜底推断: 描述含起火关键词→起火");
            }
            if (desc.contains("翻车") || desc.contains("翻滚") || desc.contains("侧翻") || desc.contains("rollover")) {
                event.setAccidentRollover(1);
                log.debug("兜底推断: 描述含翻车关键词→翻车");
            }
            if (desc.contains("伤亡") || desc.contains("受伤") || desc.contains("死亡") || desc.contains("casualty")) {
                if (event.getAccidentCasualty() == null || event.getAccidentCasualty() == 0) {
                    event.setAccidentCasualty(1);
                    log.debug("兜底推断: 描述含伤亡关键词→伤亡1人");
                }
            }
            if (desc.contains("追尾") || desc.contains("碰撞") || desc.contains("连环")) {
                if (event.getAccidentVehicles() == null || event.getAccidentVehicles() < 2) {
                    event.setAccidentVehicles(2);
                    log.debug("兜底推断: 描述含追尾/碰撞关键词→2辆车");
                }
            }
        }
    }

    private boolean checkBboxOverlap(List<Map<String, Object>> trackData) {
        List<double[]> boxes = new ArrayList<>();
        for (Map<String, Object> track : trackData) {
            Object bboxObj = track.get("bbox");
            if (bboxObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> b = (Map<String, Object>) bboxObj;
                double x1 = toDouble(b.get("x1")), y1 = toDouble(b.get("y1"));
                double x2 = toDouble(b.get("x2")), y2 = toDouble(b.get("y2"));
                if (x2 > x1 && y2 > y1) boxes.add(new double[]{x1, y1, x2, y2});
            }
        }
        if (boxes.size() < 2) return false;
        int overlapCount = 0;
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                double overlap = computeIoU(boxes.get(i), boxes.get(j));
                if (overlap > 0.1) overlapCount++;
            }
        }
        return overlapCount >= 1;
    }

    private boolean checkTrackSpeeds(List<Map<String, Object>> trackData) {
        for (Map<String, Object> track : trackData) {
            Object velObj = track.get("velocity");
            if (velObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Number> vel = (List<Number>) velObj;
                if (vel.size() >= 2) {
                    double speed = Math.sqrt(vel.get(0).doubleValue() * vel.get(0).doubleValue()
                            + vel.get(1).doubleValue() * vel.get(1).doubleValue());
                    if (speed > 15) return true;
                }
            }
        }
        return false;
    }

    private double computeIoU(double[] a, double[] b) {
        double x1 = Math.max(a[0], b[0]), y1 = Math.max(a[1], b[1]);
        double x2 = Math.min(a[2], b[2]), y2 = Math.min(a[3], b[3]);
        double inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        double areaA = (a[2] - a[0]) * (a[3] - a[1]);
        double areaB = (b[2] - b[0]) * (b[3] - b[1]);
        double union = areaA + areaB - inter;
        return union > 0 ? inter / union : 0;
    }

    private double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0;
    }

    private void setAccidentFeatures(AlertEvent event, AiEventCallbackRequest request) {
        if (request == null) return;
        if (request.getAccidentVehicles() != null) event.setAccidentVehicles(request.getAccidentVehicles());
        if (request.getAccidentDeformationLevel() != null) event.setAccidentDeformationLevel(request.getAccidentDeformationLevel());
        if (request.getAccidentRollover() != null) event.setAccidentRollover(request.getAccidentRollover());
        if (request.getAccidentFire() != null) event.setAccidentFire(request.getAccidentFire());
        if (request.getAccidentCasualty() != null) event.setAccidentCasualty(request.getAccidentCasualty());
        if (request.getAccidentImpactSpeed() != null) event.setAccidentImpactSpeed(request.getAccidentImpactSpeed());
    }

    private int calculateScore(AlertEvent event) {
        int score = 0;

        if (event.getAccidentFire() != null && event.getAccidentFire() == 1) {
            score += 8;
        }

        if (event.getAccidentCasualty() != null && event.getAccidentCasualty() > 0) {
            score += Math.min(event.getAccidentCasualty() * 4, 10);
        }

        if (event.getAccidentRollover() != null && event.getAccidentRollover() == 1) {
            score += 6;
        }

        if (event.getAccidentDeformationLevel() != null) {
            switch (event.getAccidentDeformationLevel()) {
                case DEFORMATION_SCORE_TOTAL: score += 7; break;
                case DEFORMATION_SCORE_SEVERE: score += 5; break;
                case DEFORMATION_SCORE_MODERATE: score += 3; break;
                case DEFORMATION_SCORE_MINOR: score += 1; break;
                default: break;
            }
        }

        if (event.getAccidentVehicles() != null && event.getAccidentVehicles() > 0) {
            if (event.getAccidentVehicles() >= 5) {
                score += 5;
            } else if (event.getAccidentVehicles() >= 3) {
                score += 3;
            } else if (event.getAccidentVehicles() >= 2) {
                score += 1;
            }
        }

        if (event.getAccidentImpactSpeed() != null) {
            double speed = event.getAccidentImpactSpeed().doubleValue();
            if (speed >= 100) {
                score += 5;
            } else if (speed >= 80) {
                score += 3;
            } else if (speed >= 60) {
                score += 2;
            } else if (speed >= 40) {
                score += 1;
            }
        }

        return score;
    }

    private AccidentSeverity resolveSeverity(int score) {
        if (score >= THRESHOLD_MAJOR) {
            return AccidentSeverity.MAJOR;
        } else if (score >= THRESHOLD_GENERAL) {
            return AccidentSeverity.GENERAL;
        }
        return AccidentSeverity.SLIGHT;
    }

    private void applySeverity(AlertEvent event, AccidentSeverity severity, List<String> reasons) {
        event.setAccidentSeverity(severity.getCode());
        event.setAccidentSeverityLabel(severity.getLabel());
        event.setAccidentPriority(severity.getPriority());

        if (reasons != null && !reasons.isEmpty()) {
            event.setAccidentEvaluationReasons(String.join("; ", reasons));
        }

        int mappedLevel = mapToEventLevel(severity);
        if (event.getEventLevel() == null || event.getEventLevel() < mappedLevel) {
            event.setEventLevel(mappedLevel);
        }
    }

    private int mapToEventLevel(AccidentSeverity severity) {
        switch (severity) {
            case MAJOR: return 3;
            case GENERAL: return 2;
            default: return 1;
        }
    }

    private List<String> buildReasons(AlertEvent event, int score, AccidentSeverity severity, boolean hasFeatures) {
        List<String> reasons = new ArrayList<>();

        if (event.getAccidentFire() != null && event.getAccidentFire() == 1) {
            reasons.add("车辆起火(+8)");
        }
        if (event.getAccidentCasualty() != null && event.getAccidentCasualty() > 0) {
            reasons.add("有人员伤亡" + event.getAccidentCasualty() + "人(+" + Math.min(event.getAccidentCasualty() * 4, 10) + ")");
        }
        if (event.getAccidentRollover() != null && event.getAccidentRollover() == 1) {
            reasons.add("车辆翻滚(+6)");
        }
        if (event.getAccidentDeformationLevel() != null) {
            switch (event.getAccidentDeformationLevel()) {
                case DEFORMATION_SCORE_TOTAL: reasons.add("严重变形/报废(+7)"); break;
                case DEFORMATION_SCORE_SEVERE: reasons.add("重度变形(+5)"); break;
                case DEFORMATION_SCORE_MODERATE: reasons.add("中度变形(+3)"); break;
                case DEFORMATION_SCORE_MINOR: reasons.add("轻微变形(+1)"); break;
                default: break;
            }
        }
        if (event.getAccidentVehicles() != null && event.getAccidentVehicles() >= 2) {
            int vs = event.getAccidentVehicles() >= 5 ? 5 : (event.getAccidentVehicles() >= 3 ? 3 : 1);
            reasons.add("涉事车辆" + event.getAccidentVehicles() + "辆(+" + vs + ")");
        }
        if (event.getAccidentImpactSpeed() != null) {
            double speed = event.getAccidentImpactSpeed().doubleValue();
            if (speed >= 100) reasons.add("碰撞车速≥100km/h(+5)");
            else if (speed >= 80) reasons.add("碰撞车速≥80km/h(+3)");
            else if (speed >= 60) reasons.add("碰撞车速≥60km/h(+2)");
            else if (speed >= 40) reasons.add("碰撞车速≥40km/h(+1)");
        }

        if (!hasFeatures && score == 0) {
            reasons.add("无明确特征，按一般事故兜底处理(默认GENERAL)");
        } else if (reasons.isEmpty()) {
            reasons.add("特征信息不足");
        }
        reasons.add("综合得分:" + score);

        return reasons;
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> result = new HashMap<>();
        result.put("MAJOR", AccidentSeverity.MAJOR);
        result.put("GENERAL", AccidentSeverity.GENERAL);
        result.put("SLIGHT", AccidentSeverity.SLIGHT);
        result.put("thresholdMajor", THRESHOLD_MAJOR);
        result.put("thresholdGeneral", THRESHOLD_GENERAL);
        return result;
    }

    public boolean isMajorAccident(AlertEvent event) {
        if (event == null || event.getAccidentSeverity() == null) return false;
        return AccidentSeverity.MAJOR.getCode().equals(event.getAccidentSeverity());
    }

    public BigDecimal getImpactSpeed(AlertEvent event) {
        if (event == null) return null;
        return event.getAccidentImpactSpeed();
    }
}

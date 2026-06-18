package com.traffic.alert.service;

import com.traffic.alert.dto.AiEventCallbackRequest;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.enums.AccidentSeverity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
            applySeverity(event, override);
            log.info("事故等级由AI直接指定: eventNo={}, severity={}({})",
                    event.getEventNo(), override.getCode(), override.getLabel());
            return override;
        }

        int totalScore = calculateScore(event);
        AccidentSeverity severity = resolveSeverity(totalScore);

        applySeverity(event, severity);

        List<String> reasons = buildReasons(event, totalScore, severity);

        log.info("事故严重程度评估完成: eventNo={}, score={}, severity={}({}), reasons={}",
                event.getEventNo(), totalScore, severity.getCode(), severity.getLabel(), reasons);

        return severity;
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

    private void applySeverity(AlertEvent event, AccidentSeverity severity) {
        event.setAccidentSeverity(severity.getCode());
        event.setAccidentSeverityLabel(severity.getLabel());
        event.setAccidentPriority(severity.getPriority());

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

    private List<String> buildReasons(AlertEvent event, int score, AccidentSeverity severity) {
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

        if (reasons.isEmpty()) {
            reasons.add("特征信息不足，按轻微事故处理");
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

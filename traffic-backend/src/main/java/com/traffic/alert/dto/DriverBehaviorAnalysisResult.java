package com.traffic.alert.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DriverBehaviorAnalysisResult {

    private Long cameraId;

    private String cameraName;

    private String cameraCode;

    private String roadName;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private LocalDateTime detectionTime;

    private String algorithmVersion;

    private Boolean isPhoneCall;

    private BigDecimal phoneCallConfidence;

    private String phoneCallRegion;

    private Boolean isYawning;

    private BigDecimal yawningConfidence;

    private BigDecimal mouthOpenRatio;

    private Boolean isFatigued;

    private BigDecimal fatigueConfidence;

    private BigDecimal eyeAspectRatio;

    private BigDecimal perclosScore;

    private Boolean isDistracted;

    private BigDecimal distractionConfidence;

    private BigDecimal headPoseYaw;

    private BigDecimal headPosePitch;

    private BigDecimal overallScore;

    private Integer behaviorLevel;

    private Boolean isAbnormal;

    private String abnormalTypes;

    private String description;

    private Boolean alertTriggered;

    private Long alertEventId;

    private Boolean ledReminded;

    private String ledRemindResult;

    private Boolean isRealFrame;

    private Long frameCaptureCostMs;

    private Integer detectionDurationMs;
}

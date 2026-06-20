package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_health_diagnosis")
public class VideoHealthDiagnosis extends BaseEntity {

    private String diagnosisNo;

    private Long cameraId;

    private String cameraName;

    private String cameraCode;

    private String roadName;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private LocalDate diagnosisDate;

    private String periodType;

    private Integer totalDetectionCount;

    private Integer abnormalCount;

    private BigDecimal normalRate;

    private BigDecimal avgBrightness;

    private BigDecimal avgContrast;

    private BigDecimal avgBlurScore;

    private BigDecimal avgOcclusionRatio;

    private BigDecimal avgOverallScore;

    private BigDecimal minOverallScore;

    private BigDecimal maxOverallScore;

    private BigDecimal healthScore;

    private Integer healthLevel;

    private String healthLevelLabel;

    private Integer blackScreenCount;

    private Integer blackScreenDuration;

    private Integer blurCount;

    private Integer occlusionCount;

    private Integer freezeCount;

    private Integer freezeDuration;

    private Integer lowBrightnessCount;

    private Integer highBrightnessCount;

    private Integer lowContrastCount;

    private Integer colorCastCount;

    private Integer alertCount;

    private Integer workOrderCount;

    private BigDecimal onlineRate;

    private Long uptimeSeconds;

    private Long downtimeSeconds;

    private Integer maintenanceStatus;

    private LocalDateTime lastMaintenanceTime;

    private LocalDateTime nextMaintenanceSuggest;

    private String diagnosisDetail;

    private String recommendation;

    private Integer status;
}

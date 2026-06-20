package com.traffic.alert.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class VideoQualityAnalysisResult {

    private Long cameraId;

    private String cameraName;

    private LocalDateTime detectionTime;

    private String frameUrl;

    private BigDecimal brightness;

    private Integer brightnessLevel;

    private BigDecimal contrast;

    private Integer contrastLevel;

    private BigDecimal blurScore;

    private Integer blurLevel;

    private BigDecimal occlusionRatio;

    private Integer occlusionLevel;

    private String occlusionRegions;

    private Boolean isBlackScreen;

    private Boolean isFrozen;

    private Integer freezeDuration;

    private BigDecimal frameChangeRate;

    private BigDecimal noiseLevel;

    private Integer colorCastLevel;

    private BigDecimal overallScore;

    private Integer qualityLevel;

    private Boolean isAbnormal;

    private String abnormalTypes;

    private String description;

    private Integer detectionDurationMs;

    private String algorithmVersion;

    private Boolean isRealFrame;

    private Long frameCaptureCostMs;
}

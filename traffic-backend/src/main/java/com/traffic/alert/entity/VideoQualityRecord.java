package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_quality_record")
public class VideoQualityRecord extends BaseEntity {

    private String recordNo;

    private Long cameraId;

    private String cameraName;

    private String cameraCode;

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

    private Integer isBlackScreen;

    private Integer isFrozen;

    private Integer freezeDuration;

    private BigDecimal frameChangeRate;

    private BigDecimal noiseLevel;

    private Integer colorCastLevel;

    private BigDecimal overallScore;

    private Integer qualityLevel;

    private Integer isAbnormal;

    private String abnormalTypes;

    private Integer alertTriggered;

    private Long alertEventId;

    private String sourceNodeCode;

    private Integer detectionDurationMs;

    private String algorithmVersion;

    private Integer isRealFrame;

    private Long frameCaptureCostMs;

    private String extraData;

    private String description;
}

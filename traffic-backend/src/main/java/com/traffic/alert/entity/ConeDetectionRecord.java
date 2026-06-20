package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cone_detection_record")
public class ConeDetectionRecord extends BaseEntity {

    private String recordNo;

    private Long planId;

    private String planCode;

    private String planName;

    private Long cameraId;

    private String cameraName;

    private LocalDateTime detectionTime;

    private Integer detectedConeCount;

    private Integer standardConeCount;

    private Integer missingConeCount;

    private Integer extraConeCount;

    private Integer isCompliant;

    private BigDecimal complianceRate;

    private String conePositions;

    private String conePositionsGis;

    private BigDecimal avgConfidence;

    private BigDecimal minConfidence;

    private String detectionAlgorithm;

    private String snapshotUrl;

    private String heatmapUrl;

    private Integer alertTriggered;

    private Integer alertLevel;

    private Long alertId;

    private String sourceNodeCode;

    private String detectionResult;

    private String description;
}

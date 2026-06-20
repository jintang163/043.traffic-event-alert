package com.traffic.alert.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DriverBehaviorCameraItem {

    private Long cameraId;

    private String cameraName;

    private String cameraCode;

    private String roadName;

    private BigDecimal avgBehaviorScore;

    private Integer behaviorLevel;

    private Integer abnormalCount;

    private String lastAbnormalType;

    private String lastAbnormalTypeName;

    private LocalDateTime lastDetectTime;

    private Integer alertCount;
}

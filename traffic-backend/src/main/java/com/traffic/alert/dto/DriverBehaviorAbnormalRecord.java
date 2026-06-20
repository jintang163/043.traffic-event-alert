package com.traffic.alert.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DriverBehaviorAbnormalRecord {

    private Long id;

    private String recordNo;

    private Long cameraId;

    private String cameraName;

    private String roadName;

    private LocalDateTime detectTime;

    private BigDecimal overallScore;

    private Integer behaviorLevel;

    private String abnormalType;

    private String abnormalTypeName;

    private String description;
}

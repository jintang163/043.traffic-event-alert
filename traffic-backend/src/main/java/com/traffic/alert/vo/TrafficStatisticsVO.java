package com.traffic.alert.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TrafficStatisticsVO {

    private Long id;

    private Long cameraId;

    private String cameraName;

    private String roadName;

    private Integer laneNo;

    private String laneName;

    private String targetClass;

    private String targetClassName;

    private LocalDateTime statTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer flowVolume;

    private BigDecimal avgSpeed;

    private BigDecimal minSpeed;

    private BigDecimal maxSpeed;

    private BigDecimal speedStandardDeviation;

    private BigDecimal occupancy;

    private BigDecimal density;

    private BigDecimal avgHeadway;

    private Integer vehicleCount;

    private String aggregateType;

    private LocalDateTime createTime;
}

package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("traffic_statistics")
public class TrafficStatistics extends BaseEntity {

    private Long cameraId;

    private String cameraName;

    private String roadName;

    private Integer laneNo;

    private String laneName;

    private String targetClass;

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
}

package com.traffic.alert.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrafficStatisticsQuery {

    private Long cameraId;

    private String cameraName;

    private Integer laneNo;

    private String roadName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String aggregateType = "minute";

    private String targetClass;
}

package com.traffic.alert.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EdgeNodeHeartbeatRequest {

    private String nodeCode;

    private String token;

    private BigDecimal cpuUsage;

    private BigDecimal memoryUsage;

    private BigDecimal gpuUsage;

    private BigDecimal temperature;

    private Integer networkStatus;

    private BigDecimal diskUsage;

    private Integer processCount;

    private Integer cameraOnlineCount;

    private Integer eventQueueSize;

    private Integer eventCountToday;

    private String extraInfo;
}

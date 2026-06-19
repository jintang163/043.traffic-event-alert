package com.traffic.alert.dto;

import lombok.Data;

@Data
public class PatrolExecutionStartRequest {

    private Long routeId;

    private Integer loopMode;

    private Integer staySeconds;
}

package com.traffic.alert.dto;

import lombok.Data;

@Data
public class DriverBehaviorManualDetectRequest {

    private Long cameraId;

    private Boolean forceMock;

    private Integer mockScenario;
}

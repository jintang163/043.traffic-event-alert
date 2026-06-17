package com.traffic.alert.dto;

import lombok.Data;

import java.util.List;

@Data
public class PtzCruiseRequest {
    private Long id;
    private Long cameraId;
    private String cruiseName;
    private Integer cruiseType;
    private Integer staySeconds;
    private Integer speed;
    private Integer loopCount;
    private Integer eventLinkage;
    private Integer eventReturnSeconds;
    private String description;
    private List<Long> presetIds;
}

package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConeDetectionQuery extends PageQuery {

    private Long planId;

    private Long cameraId;

    private Integer isCompliant;

    private Integer alertTriggered;

    private LocalDateTime detectionTimeStart;

    private LocalDateTime detectionTimeEnd;
}

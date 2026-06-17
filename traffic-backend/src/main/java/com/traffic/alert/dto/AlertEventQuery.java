package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AlertEventQuery extends PageQuery {

    private String eventType;
    private Integer eventLevel;
    private Integer alertStatus;
    private Long cameraId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer isFalsePositive;
}

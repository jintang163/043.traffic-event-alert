package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class GlobalTrackQuery extends PageQuery {
    private String keyword;
    private String trackNo;
    private String targetClass;
    private String licensePlate;
    private Long cameraId;
    private Integer trackStatus;
    private Integer isEventTarget;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}

package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class VideoClipQuery extends PageQuery {

    private Long cameraId;
    private Long alertEventId;
    private String clipType;
    private String eventType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}

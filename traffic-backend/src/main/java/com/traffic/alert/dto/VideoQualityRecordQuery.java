package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class VideoQualityRecordQuery extends PageQuery {

    private String keyword;

    private Long cameraId;

    private String cameraCode;

    private Integer qualityLevel;

    private Integer isAbnormal;

    private String abnormalType;

    private Integer alertTriggered;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}

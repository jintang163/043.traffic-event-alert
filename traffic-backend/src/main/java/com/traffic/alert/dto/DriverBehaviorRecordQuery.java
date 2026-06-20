package com.traffic.alert.dto;

import com.traffic.alert.common.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class DriverBehaviorRecordQuery extends BaseQuery {

    private Long cameraId;

    private String keyword;

    private Integer isAbnormal;

    private Integer behaviorLevel;

    private String abnormalType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer alertTriggered;

    private Integer ledReminded;

    private Integer isRealFrame;
}

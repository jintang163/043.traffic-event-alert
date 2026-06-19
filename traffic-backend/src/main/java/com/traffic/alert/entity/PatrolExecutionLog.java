package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("patrol_execution_log")
public class PatrolExecutionLog extends BaseEntity {

    private Long routeId;

    private String routeName;

    private Long startUserId;

    private String startUserName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer executionStatus;

    private Integer totalPoints;

    private Integer completedPoints;

    private String detectedEvents;

    private String remark;
}

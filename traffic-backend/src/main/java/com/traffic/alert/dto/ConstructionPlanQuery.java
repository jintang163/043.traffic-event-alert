package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConstructionPlanQuery extends PageQuery {

    private Integer constructionType;

    private Long cameraId;

    private Integer planStatus;

    private Integer alertEnabled;

    private LocalDateTime planStartTimeStart;

    private LocalDateTime planStartTimeEnd;

    private LocalDateTime planEndTimeStart;

    private LocalDateTime planEndTimeEnd;
}

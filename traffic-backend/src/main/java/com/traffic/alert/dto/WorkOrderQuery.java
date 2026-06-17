package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderQuery extends PageQuery {

    private String eventType;
    private Integer orderLevel;
    private Integer orderStatus;
    private Long assignDeptId;
    private Long assignUserId;
}

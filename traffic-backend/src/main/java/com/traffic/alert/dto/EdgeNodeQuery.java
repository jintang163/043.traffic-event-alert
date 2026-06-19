package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EdgeNodeQuery extends PageQuery {

    private Integer status;

    private Integer onlineStatus;

    private Long deptId;
}

package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OnDutyQuery extends PageQuery {

    private Long userId;
    private String dutyDate;
    private Integer dutyType;
    private Integer status;
}

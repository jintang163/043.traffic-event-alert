package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PolicePushQuery extends PageQuery {

    private String eventNo;
    private String plateNumber;
    private Integer pushStatus;
    private String pushTarget;
    private String startTime;
    private String endTime;
}

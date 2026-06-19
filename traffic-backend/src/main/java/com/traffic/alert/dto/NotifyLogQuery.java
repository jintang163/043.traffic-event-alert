package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NotifyLogQuery extends PageQuery {

    private String eventNo;
    private String channelType;
    private Integer sendStatus;
    private String startTime;
    private String endTime;
}

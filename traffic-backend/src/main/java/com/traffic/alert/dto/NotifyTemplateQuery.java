package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NotifyTemplateQuery extends PageQuery {

    private String channelType;
    private String eventType;
    private Integer eventLevel;
    private Integer status;
}

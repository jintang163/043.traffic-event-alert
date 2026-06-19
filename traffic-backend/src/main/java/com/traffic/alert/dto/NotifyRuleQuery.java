package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NotifyRuleQuery extends PageQuery {

    private String eventType;
    private Integer eventLevel;
    private Long channelId;
    private Integer enabled;
}

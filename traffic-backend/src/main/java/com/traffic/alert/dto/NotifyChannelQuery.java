package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NotifyChannelQuery extends PageQuery {

    private String channelType;
    private Integer enabled;
}

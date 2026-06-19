package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_channel")
public class NotifyChannel extends BaseEntity {

    private String channelCode;

    private String channelName;

    private String channelType;

    private Integer enabled;

    private String configJson;

    private String description;

    private Integer sortOrder;
}

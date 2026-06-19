package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_template")
public class NotifyTemplate extends BaseEntity {

    private String templateCode;

    private String templateName;

    private String channelType;

    private String eventType;

    private Integer eventLevel;

    private String titleTemplate;

    private String contentTemplate;

    private Integer status;

    private String description;
}

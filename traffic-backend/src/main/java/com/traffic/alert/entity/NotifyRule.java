package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_rule")
public class NotifyRule extends BaseEntity {

    private String ruleName;

    private String eventType;

    private Integer eventLevel;

    private Long channelId;

    private Long templateId;

    private Integer recipientType;

    private String recipientIds;

    private Integer atAll;

    private Integer enabled;

    private Integer priority;

    private Integer sortOrder;

    private String description;
}

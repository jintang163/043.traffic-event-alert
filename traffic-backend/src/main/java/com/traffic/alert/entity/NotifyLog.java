package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_log")
public class NotifyLog extends BaseEntity {

    private String logNo;

    private Long alertEventId;

    private String eventNo;

    private Long channelId;

    private String channelType;

    private Long templateId;

    private Integer recipientType;

    private String recipientInfo;

    private String title;

    private String content;

    private Integer sendStatus;

    private Integer retryCount;

    private Integer maxRetry;

    private LocalDateTime nextRetryTime;

    private String responseBody;

    private String errorMessage;

    private LocalDateTime sendTime;

    private LocalDateTime successTime;

    private Long costMs;
}

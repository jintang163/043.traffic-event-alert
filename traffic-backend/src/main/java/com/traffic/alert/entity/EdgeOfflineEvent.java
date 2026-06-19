package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edge_offline_event")
public class EdgeOfflineEvent extends BaseEntity {

    private Long edgeNodeId;

    private String nodeCode;

    private String eventUuid;

    private String eventData;

    private String eventType;

    private LocalDateTime eventTime;

    private String snapshotPath;

    private String videoPath;

    private Integer uploadStatus;

    private Integer retryCount;

    private Integer maxRetry;

    private LocalDateTime nextRetryTime;

    private LocalDateTime uploadTime;

    private String errorMessage;
}

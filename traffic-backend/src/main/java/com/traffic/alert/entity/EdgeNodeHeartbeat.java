package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edge_node_heartbeat")
public class EdgeNodeHeartbeat extends BaseEntity {

    private Long edgeNodeId;

    private String nodeCode;

    private BigDecimal cpuUsage;

    private BigDecimal memoryUsage;

    private BigDecimal gpuUsage;

    private BigDecimal temperature;

    private Integer networkStatus;

    private BigDecimal diskUsage;

    private Integer processCount;

    private Integer cameraOnlineCount;

    private Integer eventQueueSize;

    private String extraInfo;
}

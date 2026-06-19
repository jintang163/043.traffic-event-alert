package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edge_node")
public class EdgeNode extends BaseEntity {

    private String nodeCode;

    private String nodeName;

    private String hardwareModel;

    private String gpuInfo;

    private Integer cpuCores;

    private Integer memoryGB;

    private Integer storageGB;

    private String osInfo;

    private String ipAddress;

    private String macAddress;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String location;

    private Integer status;

    private Integer onlineStatus;

    private LocalDateTime lastHeartbeat;

    private Integer heartbeatInterval;

    private BigDecimal cpuUsage;

    private BigDecimal memoryUsage;

    private BigDecimal gpuUsage;

    private BigDecimal temperature;

    private Integer cameraCount;

    private Integer eventCountToday;

    private String description;

    private Long deptId;

    private String configJson;
}

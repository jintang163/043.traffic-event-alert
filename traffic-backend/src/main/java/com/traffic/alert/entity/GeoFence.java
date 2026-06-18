package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_fence")
public class GeoFence extends BaseEntity {

    private String fenceCode;

    private String fenceName;

    private Integer fenceType;

    private Long cameraId;

    private String cameraName;

    private String polygonPoints;

    private BigDecimal centerLongitude;

    private BigDecimal centerLatitude;

    private BigDecimal area;

    private Integer alertEnabled;

    private Integer alertLevel;

    private String detectTargetTypes;

    private Integer staySeconds;

    private Integer cooldownSeconds;

    private Integer notifyEnabled;

    private String notifyDeptIds;

    private Integer linkWorkOrder;

    private String color;

    private String description;

    private Integer sortOrder;

    private Integer status;
}

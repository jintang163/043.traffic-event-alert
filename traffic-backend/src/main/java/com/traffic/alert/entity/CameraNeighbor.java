package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("camera_neighbor")
public class CameraNeighbor extends BaseEntity {

    private Long cameraId;

    private Long neighborCameraId;

    private String neighborCameraName;

    private Integer direction;

    private BigDecimal distance;

    private Integer travelTimeSeconds;

    private Integer priority;

    private String description;
}

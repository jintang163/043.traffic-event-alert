package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ptz_cruise")
public class PtzCruise extends BaseEntity {

    private Long cameraId;

    private String cruiseName;

    private Integer cruiseType;

    private Integer status;

    private Integer staySeconds;

    private Integer speed;

    private Integer loopCount;

    private Integer eventLinkage;

    private Integer eventReturnSeconds;

    private String description;
}

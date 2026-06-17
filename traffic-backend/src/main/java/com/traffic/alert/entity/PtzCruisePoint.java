package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ptz_cruise_point")
public class PtzCruisePoint extends BaseEntity {

    private Long cruiseId;

    private Long presetId;

    private Integer presetIndex;

    private String presetName;

    private Integer staySeconds;

    private Integer sortOrder;
}

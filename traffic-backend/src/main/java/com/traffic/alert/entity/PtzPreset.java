package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ptz_preset")
public class PtzPreset extends BaseEntity {

    private Long cameraId;

    private Integer presetIndex;

    private String presetName;

    private BigDecimal pan;

    private BigDecimal tilt;

    private BigDecimal zoom;

    private String thumbnailUrl;

    private Integer sortOrder;
}

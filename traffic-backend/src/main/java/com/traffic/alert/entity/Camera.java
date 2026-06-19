package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("camera")
public class Camera extends BaseEntity {

    private String cameraCode;

    private String cameraName;

    private String protocol;

    private String streamUrl;

    private String gbDeviceId;

    private String manufacturer;

    private String location;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String roadName;

    private Integer direction;

    private Integer laneCount;

    private Integer status;

    private Integer onlineStatus;

    private Integer ptzEnabled;

    private String ptzPresets;

    private String locationCode;

    private String description;
}

package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CameraQuery extends PageQuery {

    private String protocol;
    private String manufacturer;
    private String roadName;
    private Integer status;
    private Integer onlineStatus;
}

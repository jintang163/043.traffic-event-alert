package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GeoFenceQuery extends PageQuery {

    private String keyword;

    private Integer fenceType;

    private Long cameraId;

    private Integer alertEnabled;

    private Integer status;
}

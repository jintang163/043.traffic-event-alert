package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("patrol_route_point")
public class PatrolRoutePoint extends BaseEntity {

    private Long routeId;

    private Long cameraId;

    private String cameraName;

    private String cameraCode;

    private Integer sortOrder;

    private Integer staySeconds;

    private Double longitude;

    private Double latitude;

    private String location;
}

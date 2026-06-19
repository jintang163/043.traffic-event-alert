package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("patrol_route")
public class PatrolRoute extends BaseEntity {

    private String routeName;

    private String routeCode;

    private String description;

    private Integer status;

    private Integer staySeconds;

    private Integer loopMode;

    private Integer createUserId;

    private String createUserName;
}

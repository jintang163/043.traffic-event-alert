package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_department")
public class Department extends BaseEntity {

    private String deptCode;

    private String deptName;

    private Integer deptType;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String contactPerson;

    private String contactPhone;

    private Integer status;

    private String description;
}

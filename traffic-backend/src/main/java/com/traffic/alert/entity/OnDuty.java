package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("on_duty")
public class OnDuty extends BaseEntity {

    private Long userId;

    private String userName;

    private String phone;

    private Long deptId;

    private String deptName;

    private LocalDate dutyDate;

    private Integer dutyType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer status;

    private String remark;
}

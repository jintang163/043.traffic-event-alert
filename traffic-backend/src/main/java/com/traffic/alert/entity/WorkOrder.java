package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_order")
public class WorkOrder extends BaseEntity {

    private String orderNo;

    private Long alertEventId;

    private String eventType;

    private String debrisCategory;

    private Integer orderLevel;

    private String title;

    private String description;

    private Long assignDeptId;

    private String assignDeptName;

    private Long assignUserId;

    private String assignUserName;

    private Integer orderStatus;

    private LocalDateTime planStartTime;

    private LocalDateTime planEndTime;

    private LocalDateTime actualStartTime;

    private LocalDateTime actualEndTime;

    private String handleContent;

    private String handleImages;

    private String remark;
}

package com.traffic.alert.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkOrderHandleRequest {

    private Integer orderStatus;
    private String handleContent;
    private String handleImages;
    private String remark;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
}

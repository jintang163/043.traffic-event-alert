package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("detection_task")
public class DetectionTask extends BaseEntity {

    private Long cameraId;

    private String taskType;

    private Integer status;

    private Integer fps;

    private String aiServer;

    private String gpuDevice;

    private LocalDateTime lastHeartbeat;
}

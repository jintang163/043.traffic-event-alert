package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_clip")
public class VideoClip extends BaseEntity {

    private Long cameraId;

    private String clipType;

    private Long alertEventId;

    private String fileName;

    private String filePath;

    private String fileUrl;

    private Long fileSize;

    private Integer duration;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String thumbnailUrl;
}

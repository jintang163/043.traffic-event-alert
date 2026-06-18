package com.traffic.alert.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "video")
public class VideoConfig {

    private String ffmpegPath = "ffmpeg";
    private String tempDir;
    private Integer preEventSeconds = 10;
    private Integer postEventSeconds = 10;
    private Integer segmentDuration = 2;
    private Integer hlsListSize = 0;
    private Boolean enabled = true;
}

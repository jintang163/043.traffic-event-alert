package com.traffic.alert.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "device.sdk")
public class DeviceSdkConfig {

    private boolean hikvisionEnabled = false;
    private boolean dahuaEnabled = false;
    private boolean gb28181Enabled = false;

    private String hikvisionSdkPath = "";
    private String dahuaSdkPath = "";

    private String sdkProxyUrl = "http://localhost:9000";

    private String hikvisionUsername = "admin";
    private String hikvisionPassword = "Admin12345";
    private Integer hikvisionPort = 8000;

    private String dahuaUsername = "admin";
    private String dahuaPassword = "admin123";
    private Integer dahuaPort = 37777;

    private String gb28181SipServer = "";
    private String gb28181SipPort = "5060";
    private String gb28181SipId = "";
    private String gb28181SipDomain = "";
    private String gb28181SipPassword = "";
    private String gb28181MediaServer = "";
    private String gb28181MediaPort = "";
}

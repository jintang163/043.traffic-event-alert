package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("police_system_config")
public class PoliceSystemConfig extends BaseEntity {

    private String systemCode;

    private String systemName;

    private String pushUrl;

    private String authType;

    private String authToken;

    private String basicUsername;

    private String basicPassword;

    private Integer enabled;

    private Integer retryMax;

    private Integer retryInitialSeconds;

    private BigDecimal retryMultiplier;

    private Integer retryMaxSeconds;

    private Integer timeoutSeconds;

    private String remark;
}

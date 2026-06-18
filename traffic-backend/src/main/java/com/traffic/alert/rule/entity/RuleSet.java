package com.traffic.alert.rule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.traffic.alert.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rule_set")
public class RuleSet extends BaseEntity {

    private String ruleCode;

    private String ruleName;

    private Integer gatewayType;

    private String description;

    private Integer status;

    private String defaultBranch;
}

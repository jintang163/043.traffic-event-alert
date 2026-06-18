package com.traffic.alert.rule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.traffic.alert.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("decision_table")
public class DecisionTable extends BaseEntity {

    private String tableCode;

    private String tableName;

    private String tableData;

    private String hitPolicy;

    private String description;

    private Integer status;
}

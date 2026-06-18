package com.traffic.alert.rule.context;

import lombok.Data;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContextFieldSpec {

    @Getter
    public enum FieldCategory {
        SYSTEM("system", "系统变量"),
        FORM("form", "表单字段"),
        BUSINESS("business", "业务数据"),
        CUSTOM("custom", "自定义");

        private final String key;
        private final String label;

        FieldCategory(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    @Getter
    public enum FieldType {
        STRING("string", "字符串"),
        NUMBER("number", "数值"),
        DECIMAL("decimal", "金额"),
        BOOLEAN("boolean", "布尔"),
        DATE("date", "日期"),
        ENUM("enum", "枚举");

        private final String key;
        private final String label;

        FieldType(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    @Data
    public static class FieldDefinition {
        private String category;
        private String field;
        private String label;
        private String type;
        private String description;
        private List<Map<String, Object>> options;
        private Object sampleValue;

        public FieldDefinition() {}

        public FieldDefinition(String category, String field, String label, String type, String description, Object sampleValue) {
            this.category = category;
            this.field = field;
            this.label = label;
            this.type = type;
            this.description = description;
            this.sampleValue = sampleValue;
        }

        public FieldDefinition options(List<Map<String, Object>> options) {
            this.options = options;
            return this;
        }
    }

    public static List<FieldDefinition> getAllFieldDefinitions() {
        List<FieldDefinition> fields = new ArrayList<>();

        fields.add(new FieldDefinition(FieldCategory.SYSTEM.getKey(), "userId", "发起人ID", FieldType.NUMBER.getKey(), "当前登录用户ID", 1001L));
        fields.add(new FieldDefinition(FieldCategory.SYSTEM.getKey(), "username", "发起人账号", FieldType.STRING.getKey(), "发起人登录账号", "zhangsan"));
        fields.add(new FieldDefinition(FieldCategory.SYSTEM.getKey(), "nickname", "发起人姓名", FieldType.STRING.getKey(), "发起人姓名", "张三"));
        fields.add(new FieldDefinition(FieldCategory.SYSTEM.getKey(), "role", "发起人角色", FieldType.NUMBER.getKey(), "0-管理员 1-操作员", 1));
        fields.add(new FieldDefinition(FieldCategory.SYSTEM.getKey(), "deptId", "发起人部门ID", FieldType.NUMBER.getKey(), "所属部门ID", 101L));
        fields.add(new FieldDefinition(FieldCategory.SYSTEM.getKey(), "deptCode", "发起人部门编码", FieldType.STRING.getKey(), "部门编码", "SALES_001"));
        fields.add(new FieldDefinition(FieldCategory.SYSTEM.getKey(), "deptName", "发起人部门名称", FieldType.STRING.getKey(), "部门名称", "销售部"));
        fields.add(new FieldDefinition(FieldCategory.SYSTEM.getKey(), "deptType", "发起人部门类型", FieldType.NUMBER.getKey(), "1-养护 2-交警", 1));
        fields.add(new FieldDefinition(FieldCategory.SYSTEM.getKey(), "currentTime", "当前时间", FieldType.DATE.getKey(), "系统当前时间", "2026-06-18 10:30:00"));

        fields.add(new FieldDefinition(FieldCategory.FORM.getKey(), "amount", "申请金额", FieldType.DECIMAL.getKey(), "订单/报销金额(元)", new BigDecimal("150000.00")));
        fields.add(new FieldDefinition(FieldCategory.FORM.getKey(), "title", "标题", FieldType.STRING.getKey(), "表单标题", "设备采购申请"));
        fields.add(new FieldDefinition(FieldCategory.FORM.getKey(), "category", "分类", FieldType.STRING.getKey(), "业务分类", "采购"));
        fields.add(new FieldDefinition(FieldCategory.FORM.getKey(), "urgency", "紧急程度", FieldType.NUMBER.getKey(), "1-一般 2-紧急 3-特急", 2));

        List<Map<String, Object>> eventTypeOptions = new ArrayList<>();
        eventTypeOptions.add(Map.of("value", "ACCIDENT", "label", "交通事故"));
        eventTypeOptions.add(Map.of("value", "REVERSE", "label", "车辆逆行"));
        eventTypeOptions.add(Map.of("value", "DEBRIS", "label", "路面抛洒物"));
        eventTypeOptions.add(Map.of("value", "INTRUSION", "label", "区域入侵"));
        fields.add(new FieldDefinition(FieldCategory.BUSINESS.getKey(), "eventType", "事件类型", FieldType.ENUM.getKey(), "告警事件类型", "ACCIDENT").options(eventTypeOptions));

        fields.add(new FieldDefinition(FieldCategory.BUSINESS.getKey(), "eventLevel", "事件级别", FieldType.NUMBER.getKey(), "1-一般 2-严重 3-紧急", 2));
        fields.add(new FieldDefinition(FieldCategory.BUSINESS.getKey(), "cameraId", "摄像头ID", FieldType.NUMBER.getKey(), "所属摄像头ID", 1L));
        fields.add(new FieldDefinition(FieldCategory.BUSINESS.getKey(), "cameraName", "摄像头名称", FieldType.STRING.getKey(), "摄像头名称", "京港澳高速K100+500北"));
        fields.add(new FieldDefinition(FieldCategory.BUSINESS.getKey(), "orderLevel", "工单级别", FieldType.NUMBER.getKey(), "1-一般 2-严重 3-紧急", 2));
        fields.add(new FieldDefinition(FieldCategory.BUSINESS.getKey(), "confidence", "置信度", FieldType.DECIMAL.getKey(), "AI识别置信度0~1", new BigDecimal("0.95")));

        return fields;
    }

    public static Map<String, Object> buildSampleContext() {
        Map<String, Object> context = new HashMap<>();
        Map<String, Object> system = new HashMap<>();
        system.put("userId", 1001L);
        system.put("username", "zhangsan");
        system.put("nickname", "张三");
        system.put("role", 1);
        system.put("deptId", 101L);
        system.put("deptCode", "SALES_001");
        system.put("deptName", "销售部");
        system.put("deptType", 1);
        system.put("currentTime", "2026-06-18 10:30:00");
        context.put("system", system);

        Map<String, Object> form = new HashMap<>();
        form.put("amount", new BigDecimal("150000.00"));
        form.put("title", "设备采购申请");
        form.put("category", "采购");
        form.put("urgency", 2);
        context.put("form", form);

        Map<String, Object> business = new HashMap<>();
        business.put("eventType", "ACCIDENT");
        business.put("eventLevel", 2);
        business.put("cameraId", 1L);
        business.put("cameraName", "京港澳高速K100+500北");
        business.put("orderLevel", 2);
        business.put("confidence", new BigDecimal("0.95"));
        context.put("business", business);

        return context;
    }
}

package com.traffic.alert.rule.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.traffic.alert.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionTableService {

    private final ExpressionEngineService expressionEngineService;

    public List<DecisionRule> parseTable(String tableData) {
        try {
            JSONObject tableJson = JSON.parseObject(tableData);
            JSONArray columns = tableJson.getJSONArray("columns");
            JSONArray rows = tableJson.getJSONArray("rows");

            if (columns == null || columns.isEmpty()) {
                throw new BusinessException("决策表列定义不能为空");
            }
            if (rows == null || rows.isEmpty()) {
                throw new BusinessException("决策表行数据不能为空");
            }

            List<DecisionColumn> conditionColumns = new ArrayList<>();
            List<DecisionColumn> actionColumns = new ArrayList<>();

            for (int i = 0; i < columns.size(); i++) {
                JSONObject col = columns.getJSONObject(i);
                DecisionColumn dc = new DecisionColumn();
                dc.setIndex(i);
                dc.setField(col.getString("field"));
                dc.setLabel(col.getString("label"));
                dc.setType(col.getString("type"));
                dc.setOperator(col.getString("operator"));
                if ("condition".equals(col.getString("columnType"))) {
                    conditionColumns.add(dc);
                } else {
                    actionColumns.add(dc);
                }
            }

            List<DecisionRule> rules = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                DecisionRule rule = new DecisionRule();
                rule.setRuleIndex(i + 1);
                rule.setDescription(row.getString("_description") != null ?
                        row.getString("_description") : "规则" + (i + 1));

                List<String> expressions = new ArrayList<>();
                for (DecisionColumn col : conditionColumns) {
                    String value = row.getString(col.getField());
                    if (value != null && !value.isEmpty() && !"-".equals(value)) {
                        String expr = buildConditionExpression(col, value);
                        expressions.add(expr);
                    }
                }
                rule.setConditionExpression(String.join(" && ", expressions));

                Map<String, Object> actions = new HashMap<>();
                for (DecisionColumn col : actionColumns) {
                    String value = row.getString(col.getField());
                    if (value != null && !value.isEmpty()) {
                        actions.put(col.getField(), parseValue(col.getType(), value));
                    }
                }
                rule.setActions(actions);
                rules.add(rule);
            }

            log.info("决策表解析完成, 共解析出 {} 条规则", rules.size());
            return rules;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("决策表解析失败: {}", e.getMessage(), e);
            throw new BusinessException("决策表解析失败: " + e.getMessage());
        }
    }

    private String buildConditionExpression(DecisionColumn col, String value) {
        String field = col.getField();
        String operator = col.getOperator() != null ? col.getOperator() : "==";
        String type = col.getType() != null ? col.getType() : "string";

        switch (operator) {
            case "==":
                if ("string".equals(type)) {
                    return field + " == '" + escapeString(value) + "'";
                } else {
                    return field + " == " + value;
                }
            case "!=":
                if ("string".equals(type)) {
                    return field + " != '" + escapeString(value) + "'";
                } else {
                    return field + " != " + value;
                }
            case ">":
            case ">=":
            case "<":
            case "<=":
                return field + " " + operator + " " + value;
            case "contains":
                return "string.contains(" + field + ", '" + escapeString(value) + "')";
            case "in":
                return "seq.contains(seq.list(" + value + "), " + field + ")";
            case "between":
                String[] parts = value.split(",");
                if (parts.length == 2) {
                    return field + " >= " + parts[0].trim() + " && " + field + " <= " + parts[1].trim();
                }
                return field + " == " + value;
            default:
                if ("string".equals(type)) {
                    return field + " == '" + escapeString(value) + "'";
                }
                return field + " == " + value;
        }
    }

    private Object parseValue(String type, String value) {
        if (type == null) {
            return value;
        }
        switch (type) {
            case "number":
                try {
                    if (value.contains(".")) {
                        return Double.parseDouble(value);
                    }
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    return value;
                }
            case "boolean":
                return Boolean.parseBoolean(value);
            default:
                return value;
        }
    }

    private String escapeString(String value) {
        return value.replace("'", "\\'");
    }

    public List<DecisionRule> evaluate(String tableData, Map<String, Object> context, String hitPolicy) {
        List<DecisionRule> allRules = parseTable(tableData);
        List<DecisionRule> matchedRules = new ArrayList<>();

        for (DecisionRule rule : allRules) {
            try {
                Boolean matched = expressionEngineService.executeBoolean(rule.getConditionExpression(), context);
                if (Boolean.TRUE.equals(matched)) {
                    rule.setMatched(true);
                    matchedRules.add(rule);
                    if ("FIRST".equalsIgnoreCase(hitPolicy)) {
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("规则 {} 执行异常: {}", rule.getRuleIndex(), e.getMessage());
            }
        }

        return matchedRules;
    }

    public Map<String, Object> evaluateToResult(String tableData, Map<String, Object> context, String hitPolicy) {
        List<DecisionRule> matchedRules = evaluate(tableData, context, hitPolicy);
        Map<String, Object> result = new HashMap<>();
        for (DecisionRule rule : matchedRules) {
            result.putAll(rule.getActions());
        }
        result.put("_matchedRules", matchedRules);
        return result;
    }

    public static class DecisionColumn {
        private Integer index;
        private String field;
        private String label;
        private String type;
        private String operator;

        public Integer getIndex() { return index; }
        public void setIndex(Integer index) { this.index = index; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
    }

    public static class DecisionRule {
        private Integer ruleIndex;
        private String description;
        private String conditionExpression;
        private Map<String, Object> actions;
        private Boolean matched;

        public Integer getRuleIndex() { return ruleIndex; }
        public void setRuleIndex(Integer ruleIndex) { this.ruleIndex = ruleIndex; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getConditionExpression() { return conditionExpression; }
        public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }
        public Map<String, Object> getActions() { return actions; }
        public void setActions(Map<String, Object> actions) { this.actions = actions; }
        public Boolean getMatched() { return matched; }
        public void setMatched(Boolean matched) { this.matched = matched; }
    }
}

package com.traffic.alert.rule.service;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.exception.ExpressionSyntaxErrorException;
import com.traffic.alert.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ExpressionEngineService {

    static {
        AviatorEvaluator.setOption(com.googlecode.aviator.Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, true);
    }

    public Map<String, Object> buildDefaultContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("system", new HashMap<String, Object>());
        context.put("form", new HashMap<String, Object>());
        return context;
    }

    public ExpressionValidateResult validate(String expression) {
        ExpressionValidateResult result = new ExpressionValidateResult();
        result.setExpression(expression);
        result.setValid(true);
        try {
            AviatorEvaluator.validate(expression);
            result.setMessage("表达式语法正确");
        } catch (ExpressionSyntaxErrorException e) {
            result.setValid(false);
            result.setMessage("表达式语法错误: " + e.getMessage());
            log.warn("表达式验证失败: {}, 错误: {}", expression, e.getMessage());
        } catch (Exception e) {
            result.setValid(false);
            result.setMessage("表达式验证异常: " + e.getMessage());
            log.warn("表达式验证异常: {}, 错误: {}", expression, e.getMessage());
        }
        return result;
    }

    public Object execute(String expression, Map<String, Object> context) {
        try {
            Expression compiled = AviatorEvaluator.compile(expression, true);
            return compiled.execute(context);
        } catch (ExpressionSyntaxErrorException e) {
            throw new BusinessException("表达式语法错误: " + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException("表达式执行异常: " + e.getMessage());
        }
    }

    public Boolean executeBoolean(String expression, Map<String, Object> context) {
        Object result = execute(expression, context);
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        if (result instanceof Number) {
            return ((Number) result).doubleValue() != 0;
        }
        return result != null;
    }

    public static class ExpressionValidateResult {
        private String expression;
        private Boolean valid;
        private String message;

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public Boolean getValid() {
            return valid;
        }

        public void setValid(Boolean valid) {
            this.valid = valid;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

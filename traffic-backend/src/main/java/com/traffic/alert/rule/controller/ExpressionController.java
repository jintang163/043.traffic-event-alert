package com.traffic.alert.rule.controller;

import com.traffic.alert.common.Result;
import com.traffic.alert.rule.service.ExpressionEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "表达式引擎")
@RestController
@RequestMapping("/api/expressions")
@RequiredArgsConstructor
public class ExpressionController {

    private final ExpressionEngineService expressionEngineService;

    @Operation(summary = "验证表达式语法")
    @PostMapping("/validate")
    public Result<ExpressionEngineService.ExpressionValidateResult> validate(@RequestBody Map<String, String> body) {
        String expression = body.get("expression");
        return Result.success(expressionEngineService.validate(expression));
    }

    @Operation(summary = "执行表达式")
    @PostMapping("/execute")
    public Result<Object> execute(@RequestBody Map<String, Object> body) {
        String expression = (String) body.get("expression");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.getOrDefault("context", expressionEngineService.buildDefaultContext());
        return Result.success(expressionEngineService.execute(expression, context));
    }

    @Operation(summary = "执行布尔表达式")
    @PostMapping("/execute-boolean")
    public Result<Boolean> executeBoolean(@RequestBody Map<String, Object> body) {
        String expression = (String) body.get("expression");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.getOrDefault("context", expressionEngineService.buildDefaultContext());
        return Result.success(expressionEngineService.executeBoolean(expression, context));
    }
}

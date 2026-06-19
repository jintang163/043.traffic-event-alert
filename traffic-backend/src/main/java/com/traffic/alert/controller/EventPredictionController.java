package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.entity.EventPrediction;
import com.traffic.alert.service.EventPredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "事件预测")
@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class EventPredictionController {

    private final EventPredictionService predictionService;

    @Operation(summary = "分页查询预测记录")
    @GetMapping("/page")
    public Result<PageResult<EventPrediction>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "风险等级") @RequestParam(required = false) Integer riskLevel) {
        return Result.success(predictionService.page(current, size, status, riskLevel));
    }

    @Operation(summary = "获取预测详情")
    @GetMapping("/{id}")
    public Result<EventPrediction> getById(@PathVariable Long id) {
        return Result.success(predictionService.getById(id));
    }

    @Operation(summary = "获取指定时间范围的预测结果")
    @GetMapping("/range")
    public Result<List<EventPrediction>> getPredictions(
            @Parameter(description = "开始时间") @RequestParam
                @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam
                @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(predictionService.getPredictions(startTime, endTime));
    }

    @Operation(summary = "获取未来1小时预测结果（最新批次）")
    @GetMapping("/next-hour")
    public Result<List<EventPrediction>> getNextHourPredictions() {
        return Result.success(predictionService.getPredictionsForNextHour());
    }

    @Operation(summary = "手动触发预测生成")
    @PostMapping("/generate")
    public Result<List<EventPrediction>> generatePredictions(
            @Parameter(description = "预测窗口小时数") @RequestParam(defaultValue = "1") int targetHours) {
        return Result.success(predictionService.generatePredictions(targetHours));
    }

    @Operation(summary = "获取预测总览统计")
    @GetMapping("/summary")
    public Result<Map<String, Object>> getPredictionSummary() {
        return Result.success(predictionService.getPredictionSummary());
    }
}

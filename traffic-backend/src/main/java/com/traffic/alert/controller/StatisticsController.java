package com.traffic.alert.controller;

import com.traffic.alert.common.Result;
import com.traffic.alert.config.InfluxDBConfig;
import com.traffic.alert.dto.TrafficStatisticsQuery;
import com.traffic.alert.service.AlertEventService;
import com.traffic.alert.service.CameraService;
import com.traffic.alert.service.TrafficStatisticsService;
import com.traffic.alert.service.WorkOrderService;
import com.traffic.alert.vo.TrafficRealtimeVO;
import com.traffic.alert.vo.TrafficStatisticsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "统计信息")
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final CameraService cameraService;
    private final AlertEventService alertEventService;
    private final WorkOrderService workOrderService;
    private final TrafficStatisticsService trafficStatisticsService;
    private final InfluxDBConfig influxDBConfig;

    @Operation(summary = "获取总览统计")
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        Map<String, Object> result = new HashMap<>();
        result.put("camera", cameraService.getStatistics());
        result.put("alert", alertEventService.getStatistics());
        result.put("workOrder", workOrderService.getStatistics());
        return Result.success(result);
    }

    @Operation(summary = "检查InfluxDB可用性")
    @GetMapping("/traffic/influxdb-status")
    public Result<Map<String, Object>> getInfluxDbStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", influxDBConfig.isEnabled());
        status.put("available", trafficStatisticsService.isInfluxDbAvailable());
        status.put("url", influxDBConfig.getUrl());
        return Result.success(status);
    }

    @Operation(summary = "获取交通态势总览")
    @GetMapping("/traffic/overview")
    public Result<Map<String, Object>> getTrafficOverview() {
        return Result.success(trafficStatisticsService.getTrafficOverview());
    }

    @Operation(summary = "查询交通统计数据（历史趋势）")
    @GetMapping("/traffic/history")
    public Result<List<TrafficStatisticsVO>> queryTrafficHistory(
            @Parameter(description = "摄像头ID") @RequestParam(required = false) Long cameraId,
            @Parameter(description = "车道号") @RequestParam(required = false) Integer laneNo,
            @Parameter(description = "开始时间") @RequestParam(required = false)
                @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false)
                @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @Parameter(description = "聚合类型: minute/hour/day") @RequestParam(defaultValue = "minute") String aggregateType,
            @Parameter(description = "数据源: mysql/influxdb") @RequestParam(defaultValue = "mysql") String dataSource) {

        TrafficStatisticsQuery query = new TrafficStatisticsQuery();
        query.setCameraId(cameraId);
        query.setLaneNo(laneNo);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setAggregateType(aggregateType);

        List<TrafficStatisticsVO> result;
        if ("influxdb".equalsIgnoreCase(dataSource) && trafficStatisticsService.isInfluxDbAvailable()) {
            result = trafficStatisticsService.queryFromInfluxDB(query);
        } else {
            result = trafficStatisticsService.queryStatistics(query);
        }
        return Result.success(result);
    }

    @Operation(summary = "获取摄像头实时交通数据")
    @GetMapping("/traffic/realtime/{cameraId}")
    public Result<List<TrafficRealtimeVO>> getRealtimeTrafficData(
            @Parameter(description = "摄像头ID") @PathVariable Long cameraId) {
        return Result.success(trafficStatisticsService.getRealtimeData(cameraId));
    }

    @Operation(summary = "手动触发交通统计聚合")
    @PostMapping("/traffic/aggregate")
    public Result<String> aggregateTrafficStatistics(
            @Parameter(description = "摄像头ID") @RequestParam(required = false) Long cameraId,
            @Parameter(description = "开始时间") @RequestParam(required = false)
                @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false)
                @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @Parameter(description = "聚合类型: minute/hour/day") @RequestParam(defaultValue = "minute") String aggregateType) {

        TrafficStatisticsQuery query = new TrafficStatisticsQuery();
        query.setCameraId(cameraId);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setAggregateType(aggregateType);

        trafficStatisticsService.aggregateStatistics(query);
        return Result.success("聚合完成");
    }

    @Operation(summary = "输出建表SQL")
    @PostMapping("/traffic/init-sql")
    public Result<String> outputInitSql() {
        trafficStatisticsService.addDatabaseTable();
        return Result.success("SQL已输出到日志");
    }
}

package com.traffic.alert.controller;

import com.traffic.alert.common.Result;
import com.traffic.alert.service.AlertEventService;
import com.traffic.alert.service.CameraService;
import com.traffic.alert.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "统计信息")
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final CameraService cameraService;
    private final AlertEventService alertEventService;
    private final WorkOrderService workOrderService;

    @Operation(summary = "获取总览统计")
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        Map<String, Object> result = new HashMap<>();
        result.put("camera", cameraService.getStatistics());
        result.put("alert", alertEventService.getStatistics());
        result.put("workOrder", workOrderService.getStatistics());
        return Result.success(result);
    }
}

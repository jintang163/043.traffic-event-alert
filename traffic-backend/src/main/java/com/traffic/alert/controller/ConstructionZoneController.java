package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.ConeDetectionQuery;
import com.traffic.alert.dto.ConstructionPlanQuery;
import com.traffic.alert.entity.ConeDetectionRecord;
import com.traffic.alert.entity.ConstructionPlan;
import com.traffic.alert.service.ConeDetectionService;
import com.traffic.alert.service.ConstructionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "施工区智能管控")
@RestController
@RequestMapping("/api/construction")
@RequiredArgsConstructor
public class ConstructionZoneController {

    private final ConstructionPlanService constructionPlanService;
    private final ConeDetectionService coneDetectionService;

    @Operation(summary = "分页查询施工计划")
    @GetMapping("/plans/page")
    public Result<PageResult<ConstructionPlan>> pagePlans(ConstructionPlanQuery query) {
        return Result.success(constructionPlanService.page(query));
    }

    @Operation(summary = "获取施工计划详情")
    @GetMapping("/plans/{id}")
    public Result<ConstructionPlan> getPlanById(@PathVariable Long id) {
        return Result.success(constructionPlanService.getById(id));
    }

    @Operation(summary = "获取进行中的施工计划")
    @GetMapping("/plans/active")
    public Result<List<ConstructionPlan>> listActivePlans() {
        return Result.success(constructionPlanService.listActive());
    }

    @Operation(summary = "按摄像头查询施工计划")
    @GetMapping("/plans/camera/{cameraId}")
    public Result<List<ConstructionPlan>> listPlansByCamera(@PathVariable Long cameraId) {
        return Result.success(constructionPlanService.listByCamera(cameraId));
    }

    @Operation(summary = "新增/更新施工计划")
    @PostMapping("/plans")
    public Result<ConstructionPlan> savePlan(@RequestBody ConstructionPlan plan) {
        return Result.success(constructionPlanService.save(plan));
    }

    @Operation(summary = "删除施工计划")
    @DeleteMapping("/plans/{id}")
    public Result<Void> deletePlan(@PathVariable Long id) {
        constructionPlanService.delete(id);
        return Result.success();
    }

    @Operation(summary = "更新施工计划状态")
    @PostMapping("/plans/{id}/status")
    public Result<ConstructionPlan> updatePlanStatus(
            @PathVariable Long id,
            @RequestParam int status) {
        return Result.success(constructionPlanService.updateStatus(id, status));
    }

    @Operation(summary = "启用/禁用施工计划告警")
    @PostMapping("/plans/{id}/alert")
    public Result<ConstructionPlan> togglePlanAlert(
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        return Result.success(constructionPlanService.toggleAlert(id, enabled));
    }

    @Operation(summary = "开始施工")
    @PostMapping("/plans/{id}/start")
    public Result<ConstructionPlan> startConstruction(@PathVariable Long id) {
        return Result.success(constructionPlanService.updateStatus(id, 2));
    }

    @Operation(summary = "完成施工")
    @PostMapping("/plans/{id}/complete")
    public Result<ConstructionPlan> completeConstruction(@PathVariable Long id) {
        return Result.success(constructionPlanService.updateStatus(id, 3));
    }

    @Operation(summary = "分页查询锥桶检测记录")
    @GetMapping("/cones/page")
    public Result<PageResult<ConeDetectionRecord>> pageConeRecords(ConeDetectionQuery query) {
        return Result.success(coneDetectionService.page(query));
    }

    @Operation(summary = "获取锥桶检测记录详情")
    @GetMapping("/cones/{id}")
    public Result<ConeDetectionRecord> getConeRecordById(@PathVariable Long id) {
        return Result.success(coneDetectionService.getById(id));
    }

    @Operation(summary = "按施工计划查询锥桶检测记录")
    @GetMapping("/cones/plan/{planId}")
    public Result<List<ConeDetectionRecord>> listConeRecordsByPlan(@PathVariable Long planId) {
        return Result.success(coneDetectionService.listByPlan(planId));
    }

    @Operation(summary = "获取施工计划最新锥桶检测记录")
    @GetMapping("/cones/plan/{planId}/latest")
    public Result<ConeDetectionRecord> getLatestConeRecord(@PathVariable Long planId) {
        return Result.success(coneDetectionService.getLatestByPlan(planId));
    }

    @Operation(summary = "新增锥桶检测记录")
    @PostMapping("/cones")
    public Result<ConeDetectionRecord> saveConeRecord(@RequestBody ConeDetectionRecord record) {
        return Result.success(coneDetectionService.save(record));
    }

    @Operation(summary = "删除锥桶检测记录")
    @DeleteMapping("/cones/{id}")
    public Result<Void> deleteConeRecord(@PathVariable Long id) {
        coneDetectionService.delete(id);
        return Result.success();
    }

    @Operation(summary = "获取施工计划概览统计")
    @GetMapping("/plans/{id}/summary")
    public Result<Map<String, Object>> getPlanSummary(@PathVariable Long id) {
        ConstructionPlan plan = constructionPlanService.getById(id);
        if (plan == null) {
            return Result.error("施工计划不存在");
        }

        ConeDetectionRecord latestCone = coneDetectionService.getLatestByPlan(id);
        long nonCompliantToday = coneDetectionService.countNonCompliantToday(id);

        return Result.success(Map.of(
                "plan", plan,
                "latestConeDetection", latestCone != null ? latestCone : Map.of(),
                "nonCompliantToday", nonCompliantToday,
                "stats", Map.of(
                        "totalEvents", plan.getEventCount() != null ? plan.getEventCount() : 0,
                        "coneAlerts", plan.getConeAlertCount() != null ? plan.getConeAlertCount() : 0,
                        "intrusionAlerts", plan.getIntrusionAlertCount() != null ? plan.getIntrusionAlertCount() : 0,
                        "speedingAlerts", plan.getSpeedingAlertCount() != null ? plan.getSpeedingAlertCount() : 0
                )
        ));
    }
}

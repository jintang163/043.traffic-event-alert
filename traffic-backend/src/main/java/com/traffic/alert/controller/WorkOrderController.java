package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.WorkOrderHandleRequest;
import com.traffic.alert.dto.WorkOrderQuery;
import com.traffic.alert.entity.WorkOrder;
import com.traffic.alert.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "工单管理")
@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @Operation(summary = "分页查询工单")
    @GetMapping("/page")
    public Result<PageResult<WorkOrder>> page(WorkOrderQuery query) {
        return Result.success(workOrderService.page(query));
    }

    @Operation(summary = "获取工单详情")
    @GetMapping("/{id}")
    public Result<WorkOrder> getById(@PathVariable Long id) {
        return Result.success(workOrderService.getById(id));
    }

    @Operation(summary = "保存工单")
    @PostMapping
    public Result<WorkOrder> save(@RequestBody WorkOrder workOrder) {
        return Result.success(workOrderService.save(workOrder));
    }

    @Operation(summary = "处理工单")
    @PostMapping("/{id}/handle")
    public Result<WorkOrder> handleOrder(@PathVariable Long id, @RequestBody WorkOrderHandleRequest request) {
        return Result.success(workOrderService.handleOrder(id, request));
    }

    @Operation(summary = "删除工单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workOrderService.delete(id);
        return Result.success();
    }

    @Operation(summary = "获取告警事件关联的工单")
    @GetMapping("/alert/{alertEventId}")
    public Result<List<WorkOrder>> listByAlertEventId(@PathVariable Long alertEventId) {
        return Result.success(workOrderService.listByAlertEventId(alertEventId));
    }

    @Operation(summary = "获取工单统计信息")
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        return Result.success(workOrderService.getStatistics());
    }
}

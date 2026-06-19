package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.EdgeNodeQuery;
import com.traffic.alert.entity.EdgeNode;
import com.traffic.alert.entity.EdgeOfflineEvent;
import com.traffic.alert.service.EdgeNodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "边缘节点管理")
@RestController
@RequestMapping("/api/edge-nodes")
@RequiredArgsConstructor
public class EdgeNodeController {

    private final EdgeNodeService edgeNodeService;

    @Operation(summary = "分页查询边缘节点")
    @GetMapping("/page")
    public Result<PageResult<EdgeNode>> page(EdgeNodeQuery query) {
        return Result.success(edgeNodeService.page(query));
    }

    @Operation(summary = "获取边缘节点列表")
    @GetMapping("/list")
    public Result<List<EdgeNode>> list() {
        return Result.success(edgeNodeService.list());
    }

    @Operation(summary = "获取边缘节点详情")
    @GetMapping("/{id}")
    public Result<EdgeNode> getById(@PathVariable Long id) {
        return Result.success(edgeNodeService.getById(id));
    }

    @Operation(summary = "保存边缘节点")
    @PostMapping
    public Result<EdgeNode> save(@RequestBody EdgeNode edgeNode) {
        return Result.success(edgeNodeService.save(edgeNode));
    }

    @Operation(summary = "删除边缘节点")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        edgeNodeService.delete(id);
        return Result.success();
    }

    @Operation(summary = "获取边缘节点统计信息")
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        return Result.success(edgeNodeService.getStatistics());
    }

    @Operation(summary = "获取节点配置")
    @GetMapping("/{id}/config")
    public Result<Map<String, Object>> getNodeConfig(@PathVariable Long id) {
        return Result.success(edgeNodeService.getNodeConfig(id));
    }

    @Operation(summary = "更新节点配置")
    @PostMapping("/{id}/config")
    public Result<Void> updateNodeConfig(@PathVariable Long id, @RequestBody Map<String, Object> config) {
        edgeNodeService.updateNodeConfig(id, config);
        return Result.success();
    }

    @Operation(summary = "查看节点离线事件")
    @GetMapping("/{id}/offline-events")
    public Result<List<EdgeOfflineEvent>> listOfflineEvents(
            @PathVariable Long id,
            @RequestParam(required = false) Integer uploadStatus) {
        return Result.success(edgeNodeService.listOfflineEvents(id, uploadStatus));
    }
}

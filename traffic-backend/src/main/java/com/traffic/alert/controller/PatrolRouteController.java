package com.traffic.alert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.PatrolExecutionStartRequest;
import com.traffic.alert.dto.PatrolRouteSaveRequest;
import com.traffic.alert.entity.PatrolExecutionLog;
import com.traffic.alert.entity.PatrolRoute;
import com.traffic.alert.entity.User;
import com.traffic.alert.service.PatrolRouteService;
import com.traffic.alert.vo.PatrolRouteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patrol/routes")
@RequiredArgsConstructor
public class PatrolRouteController {

    private final PatrolRouteService patrolRouteService;

    @GetMapping("/page")
    public Result<PageResult<PatrolRoute>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        Page<PatrolRoute> page = patrolRouteService.page(current, size, keyword);
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/list")
    public Result<List<PatrolRoute>> list() {
        return Result.success(patrolRouteService.list());
    }

    @GetMapping("/{id}")
    public Result<PatrolRouteVO> getDetail(@PathVariable Long id) {
        return Result.success(patrolRouteService.getDetail(id));
    }

    @PostMapping
    public Result<PatrolRoute> save(@RequestBody PatrolRouteSaveRequest request) {
        return Result.success(patrolRouteService.save(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        patrolRouteService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/start")
    public Result<Long> startExecution(
            @PathVariable Long id,
            @RequestBody(required = false) PatrolExecutionStartRequest request,
            @AuthenticationPrincipal User user) {
        Integer loopMode = request != null ? request.getLoopMode() : null;
        Integer staySeconds = request != null ? request.getStaySeconds() : null;
        Long logId = patrolRouteService.startExecution(id, user.getId(), user.getNickname(), loopMode, staySeconds);
        return Result.success(logId);
    }

    @PostMapping("/execution/{logId}/complete")
    public Result<Void> completeExecution(
            @PathVariable Long logId,
            @RequestParam(required = false) String detectedEvents,
            @RequestParam(required = false) String remark) {
        patrolRouteService.completeExecution(logId, detectedEvents, remark);
        return Result.success();
    }

    @PostMapping("/execution/{logId}/progress")
    public Result<Void> updateProgress(
            @PathVariable Long logId,
            @RequestParam int completedPoints,
            @RequestParam(required = false) String detectedEvents) {
        patrolRouteService.updateExecutionProgress(logId, completedPoints, detectedEvents);
        return Result.success();
    }

    @GetMapping("/execution/logs")
    public Result<PageResult<PatrolExecutionLog>> listExecutionLogs(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long routeId) {
        Page<PatrolExecutionLog> page = patrolRouteService.listExecutionLogs(current, size, routeId);
        return Result.success(PageResult.of(page));
    }
}

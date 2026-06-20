package com.traffic.alert.controller;

import com.traffic.alert.common.Result;
import com.traffic.alert.service.AlertDeduplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(name = "告警合并与风暴抑制")
@RestController
@RequestMapping("/api/alert-deduplication")
@RequiredArgsConstructor
public class AlertDeduplicationController {

    private final AlertDeduplicationService alertDeduplicationService;

    @Operation(summary = "获取去重服务状态")
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        return Result.success(alertDeduplicationService.getStatus());
    }

    @Operation(summary = "手动解除指定摄像头的风暴抑制")
    @PostMapping("/release/{cameraId}")
    public Result<Boolean> releaseSuppression(@PathVariable Long cameraId) {
        boolean released = alertDeduplicationService.releaseSuppression(cameraId);
        if (released) {
            log.info("API调用: 手动解除摄像头[{}]的风暴抑制", cameraId);
        }
        return Result.success(released);
    }

    @Operation(summary = "手动解除所有摄像头的风暴抑制")
    @PostMapping("/release-all")
    public Result<Void> releaseAllSuppression() {
        alertDeduplicationService.releaseAllSuppression();
        log.info("API调用: 手动解除所有摄像头的风暴抑制");
        return Result.success();
    }
}

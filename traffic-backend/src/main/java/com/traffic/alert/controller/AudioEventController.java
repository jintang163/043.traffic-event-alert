package com.traffic.alert.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.AudioEventCallbackRequest;
import com.traffic.alert.entity.AudioEvent;
import com.traffic.alert.service.AudioEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(name = "音频事件检测")
@RestController
@RequestMapping("/api/ai/audio")
@RequiredArgsConstructor
public class AudioEventController {

    private final AudioEventService audioEventService;

    @Operation(summary = "AI引擎音频事件回调")
    @PostMapping("/callback")
    public Result<AudioEvent> audioEventCallback(@RequestBody AudioEventCallbackRequest request) {
        log.info("收到音频事件回调: cameraId={}, eventType={}, duration={}",
                request.getCameraId(), request.getEventType(),
                request.getDuration() != null ? request.getDuration().toPlainString() : "0");
        return Result.success(audioEventService.handleAudioEventCallback(request));
    }

    @Operation(summary = "分页查询音频事件")
    @GetMapping("/page")
    public Result<IPage<AudioEvent>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Integer alertStatus,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime
    ) {
        return Result.success(audioEventService.page(current, size, cameraId, eventType, alertStatus, startTime, endTime));
    }

    @Operation(summary = "获取音频事件详情")
    @GetMapping("/{id}")
    public Result<AudioEvent> getById(@PathVariable Long id) {
        return Result.success(audioEventService.getById(id));
    }

    @Operation(summary = "音频事件统计")
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(audioEventService.getStatistics());
    }
}

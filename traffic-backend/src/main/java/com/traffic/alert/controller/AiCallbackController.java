package com.traffic.alert.controller;

import com.alibaba.fastjson2.JSON;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.AiEventCallbackRequest;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.service.AiEngineService;
import com.traffic.alert.service.AlertEventService;
import com.traffic.alert.websocket.AlertWebSocket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@Tag(name = "AI引擎接口")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiCallbackController {

    private final AlertEventService alertEventService;
    private final AiEngineService aiEngineService;

    @Operation(summary = "AI事件回调")
    @PostMapping("/event/callback")
    public Result<AlertEvent> eventCallback(@RequestBody AiEventCallbackRequest request) {
        log.info("收到AI事件回调: cameraId={}, eventType={}, hasVideo={}",
                request.getCameraId(), request.getEventType(),
                request.getEventVideo() != null && !request.getEventVideo().isEmpty());
        return Result.success(alertEventService.handleAiEventCallback(request));
    }

    @Operation(summary = "上传事件视频片段")
    @PostMapping("/event/upload-video")
    public Result<String> uploadEventVideo(
            @RequestParam("video") MultipartFile video,
            @RequestParam(value = "cameraId", required = false) Long cameraId,
            @RequestParam(value = "eventNo", required = false) String eventNo
    ) {
        log.info("收到事件视频上传: cameraId={}, eventNo={}, size={}KB, name={}",
                cameraId, eventNo,
                video != null ? video.getSize() / 1024 : 0,
                video != null ? video.getOriginalFilename() : "null");
        return Result.success(alertEventService.uploadEventVideo(cameraId, eventNo, video));
    }

    @Operation(summary = "推送实时检测框数据")
    @PostMapping("/detection/push")
    public Result<Void> pushDetections(@RequestBody Map<String, Object> payload) {
        try {
            Long cameraId = Long.valueOf(payload.get("cameraId").toString());
            if (log.isDebugEnabled()) {
                log.debug("收到检测框推送: cameraId={}, detections={}",
                        cameraId, JSON.toJSONString(payload.get("detections")));
            }
            AlertWebSocket.sendDetectionMessage(cameraId, payload);
        } catch (Exception e) {
            log.warn("处理检测框推送失败: {}", e.getMessage());
        }
        return Result.success();
    }

    @Operation(summary = "图像检测")
    @PostMapping("/detect/image")
    public Result<Map<String, Object>> detectImage(@RequestParam("image") MultipartFile image) {
        return Result.success(aiEngineService.detectImage(image));
    }

    @Operation(summary = "启动视频流检测")
    @PostMapping("/detect/stream/start")
    public Result<Map<String, Object>> startStreamDetection(@RequestBody Map<String, Object> params) {
        Long cameraId = Long.valueOf(params.get("cameraId").toString());
        String streamUrl = params.get("streamUrl") != null ? params.get("streamUrl").toString() : null;
        log.info("请求启动流检测: cameraId={}, streamUrl={}", cameraId, streamUrl);
        return Result.success(aiEngineService.startStreamDetection(cameraId, streamUrl));
    }

    @Operation(summary = "停止视频流检测")
    @PostMapping("/detect/stream/{cameraId}/stop")
    public Result<Map<String, Object>> stopStreamDetection(@PathVariable Long cameraId) {
        log.info("请求停止流检测: cameraId={}", cameraId);
        return Result.success(aiEngineService.stopStreamDetection(cameraId));
    }

    @Operation(summary = "事件分析")
    @PostMapping("/event/analyze")
    public Result<Map<String, Object>> analyzeEvent(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> tracks = (java.util.List<Map<String, Object>>) params.get("tracks");
        return Result.success(aiEngineService.analyzeEvent(tracks));
    }
}

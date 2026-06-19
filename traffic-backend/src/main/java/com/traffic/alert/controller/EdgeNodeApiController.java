package com.traffic.alert.controller;

import com.alibaba.fastjson2.JSON;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.EdgeEventAckRequest;
import com.traffic.alert.dto.EdgeEventBatchUploadRequest;
import com.traffic.alert.dto.EdgeEventUploadRequest;
import com.traffic.alert.dto.EdgeNodeHeartbeatRequest;
import com.traffic.alert.dto.EdgeNodeRegisterRequest;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.entity.EdgeNode;
import com.traffic.alert.entity.EdgeOfflineEvent;
import com.traffic.alert.service.EdgeNodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "边缘节点开放接口")
@RestController
@RequestMapping("/api/edge")
@RequiredArgsConstructor
public class EdgeNodeApiController {

    private final EdgeNodeService edgeNodeService;

    @Operation(summary = "边缘节点注册")
    @PostMapping("/register")
    public Result<EdgeNode> register(@RequestBody EdgeNodeRegisterRequest request) {
        log.info("边缘节点注册请求: nodeCode={}", request.getNodeCode());
        return Result.success(edgeNodeService.register(request));
    }

    @Operation(summary = "边缘节点心跳上报")
    @PostMapping("/heartbeat")
    public Result<Map<String, Object>> heartbeat(@RequestBody EdgeNodeHeartbeatRequest request) {
        return Result.success(edgeNodeService.heartbeat(request));
    }

    @Operation(summary = "事件上传（支持multipart截图视频）")
    @PostMapping("/event/upload")
    public Result<Map<String, Object>> uploadEvent(
            @RequestParam(value = "data", required = false) String dataJson,
            @RequestBody(required = false) EdgeEventUploadRequest bodyRequest,
            @RequestParam(value = "snapshot", required = false) MultipartFile snapshot,
            @RequestParam(value = "video", required = false) MultipartFile video) {

        EdgeEventUploadRequest request = bodyRequest;
        if (request == null && dataJson != null && !dataJson.isEmpty()) {
            try {
                request = JSON.parseObject(dataJson, EdgeEventUploadRequest.class);
            } catch (Exception e) {
                log.warn("解析事件数据JSON失败: {}", e.getMessage());
            }
        }
        if (request == null) {
            return Result.error("事件数据不能为空");
        }

        log.info("边缘节点事件上传: nodeCode={}, eventUuid={}, eventType={}, hasSnapshot={}, hasVideo={}",
                request.getNodeCode(), request.getEventUuid(), request.getEventType(),
                snapshot != null && !snapshot.isEmpty(), video != null && !video.isEmpty());

        try {
            AlertEvent alertEvent = edgeNodeService.processEdgeEvent(request, snapshot, video);
            Map<String, Object> result = new HashMap<>();
            result.put("eventUuid", request.getEventUuid());
            result.put("eventNo", alertEvent.getEventNo());
            result.put("alertEventId", alertEvent.getId());
            result.put("received", true);
            result.put("serverTime", LocalDateTime.now());
            return Result.success(result);
        } catch (Exception e) {
            log.error("边缘事件处理失败: eventUuid={}, err={}", request.getEventUuid(), e.getMessage());
            EdgeOfflineEvent offlineEvent = new EdgeOfflineEvent();
            offlineEvent.setNodeCode(request.getNodeCode());
            offlineEvent.setEventUuid(request.getEventUuid());
            offlineEvent.setEventType(request.getEventType());
            offlineEvent.setEventData(request.getEventData());
            offlineEvent.setEventTime(request.getEventTime() != null ? request.getEventTime() : LocalDateTime.now());
            offlineEvent.setUploadStatus(1);
            offlineEvent.setRetryCount(0);
            offlineEvent.setMaxRetry(5);
            try {
                edgeNodeService.saveOfflineEvent(offlineEvent);
            } catch (Exception ignored) {}
            Map<String, Object> result = new HashMap<>();
            result.put("eventUuid", request.getEventUuid());
            result.put("received", true);
            result.put("serverTime", LocalDateTime.now());
            return Result.success(result);
        }
    }

    @Operation(summary = "批量事件补传")
    @PostMapping("/events/batch-upload")
    public Result<Map<String, Object>> batchUploadEvents(@RequestBody EdgeEventBatchUploadRequest request) {
        log.info("边缘节点批量事件补传: nodeCode={}, count={}",
                request.getNodeCode(), request.getEvents() != null ? request.getEvents().size() : 0);
        int successCount = 0;
        int failCount = 0;
        if (request.getEvents() != null) {
            for (EdgeEventUploadRequest item : request.getEvents()) {
                if (item.getNodeCode() == null || item.getNodeCode().isEmpty()) {
                    item.setNodeCode(request.getNodeCode());
                }
                try {
                    AlertEvent alertEvent = edgeNodeService.processEdgeEvent(item, null, null);
                    if (alertEvent != null) successCount++;
                    else failCount++;
                } catch (Exception e) {
                    log.warn("批量上传事件处理失败: eventUuid={}, err={}", item.getEventUuid(), e.getMessage());
                    failCount++;
                    try {
                        EdgeOfflineEvent offlineEvent = new EdgeOfflineEvent();
                        offlineEvent.setNodeCode(item.getNodeCode());
                        offlineEvent.setEventUuid(item.getEventUuid());
                        offlineEvent.setEventType(item.getEventType());
                        offlineEvent.setEventData(item.getEventData());
                        offlineEvent.setEventTime(item.getEventTime() != null ? item.getEventTime() : LocalDateTime.now());
                        offlineEvent.setUploadStatus(1);
                        offlineEvent.setRetryCount(0);
                        offlineEvent.setMaxRetry(5);
                        edgeNodeService.saveOfflineEvent(offlineEvent);
                    } catch (Exception ignored) {}
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", request.getEvents() != null ? request.getEvents().size() : 0);
        result.put("success", successCount);
        result.put("fail", failCount);
        result.put("serverTime", LocalDateTime.now());
        return Result.success(result);
    }

    @Operation(summary = "拉取节点配置")
    @GetMapping("/{nodeCode}/config")
    public Result<Map<String, Object>> getNodeConfig(@PathVariable String nodeCode) {
        EdgeNode node = edgeNodeService.list().stream()
                .filter(n -> nodeCode.equals(n.getNodeCode()))
                .findFirst()
                .orElse(null);
        if (node == null) {
            return Result.error("节点不存在");
        }
        return Result.success(edgeNodeService.getNodeConfig(node.getId()));
    }

    @Operation(summary = "事件上传确认")
    @PostMapping("/{nodeCode}/event-ack")
    public Result<Map<String, Object>> ackEvents(
            @PathVariable String nodeCode,
            @RequestBody EdgeEventAckRequest request) {
        log.info("边缘节点事件确认: nodeCode={}, count={}", nodeCode,
                request.getEventUuids() != null ? request.getEventUuids().size() : 0);
        edgeNodeService.ackOfflineEvents(nodeCode, request.getEventUuids());
        Map<String, Object> result = new HashMap<>();
        result.put("acknowledged", true);
        result.put("count", request.getEventUuids() != null ? request.getEventUuids().size() : 0);
        result.put("serverTime", LocalDateTime.now());
        return Result.success(result);
    }
}

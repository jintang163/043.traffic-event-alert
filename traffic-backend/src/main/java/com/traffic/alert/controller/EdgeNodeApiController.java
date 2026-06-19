package com.traffic.alert.controller;

import com.traffic.alert.common.Result;
import com.traffic.alert.dto.EdgeEventAckRequest;
import com.traffic.alert.dto.EdgeEventBatchUploadRequest;
import com.traffic.alert.dto.EdgeEventUploadRequest;
import com.traffic.alert.dto.EdgeNodeHeartbeatRequest;
import com.traffic.alert.dto.EdgeNodeRegisterRequest;
import com.traffic.alert.entity.EdgeNode;
import com.traffic.alert.entity.EdgeOfflineEvent;
import com.traffic.alert.service.EdgeNodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "事件上传")
    @PostMapping("/event/upload")
    public Result<Map<String, Object>> uploadEvent(@RequestBody EdgeEventUploadRequest request) {
        log.info("边缘节点事件上传: nodeCode={}, eventUuid={}, eventType={}",
                request.getNodeCode(), request.getEventUuid(), request.getEventType());
        EdgeOfflineEvent event = new EdgeOfflineEvent();
        event.setNodeCode(request.getNodeCode());
        event.setEventUuid(request.getEventUuid());
        event.setEventType(request.getEventType());
        event.setEventData(request.getEventData());
        event.setEventTime(request.getEventTime());
        event.setUploadStatus(1);
        event.setRetryCount(0);
        event.setMaxRetry(3);
        edgeNodeService.saveOfflineEvent(event);
        Map<String, Object> result = new HashMap<>();
        result.put("eventUuid", request.getEventUuid());
        result.put("received", true);
        result.put("serverTime", LocalDateTime.now());
        return Result.success(result);
    }

    @Operation(summary = "批量事件补传")
    @PostMapping("/events/batch-upload")
    public Result<Map<String, Object>> batchUploadEvents(@RequestBody EdgeEventBatchUploadRequest request) {
        log.info("边缘节点批量事件补传: nodeCode={}, count={}",
                request.getNodeCode(), request.getEvents() != null ? request.getEvents().size() : 0);
        int successCount = 0;
        if (request.getEvents() != null) {
            for (EdgeEventUploadRequest item : request.getEvents()) {
                try {
                    EdgeOfflineEvent event = new EdgeOfflineEvent();
                    event.setNodeCode(request.getNodeCode());
                    event.setEventUuid(item.getEventUuid());
                    event.setEventType(item.getEventType());
                    event.setEventData(item.getEventData());
                    event.setEventTime(item.getEventTime());
                    event.setUploadStatus(1);
                    event.setRetryCount(0);
                    event.setMaxRetry(3);
                    edgeNodeService.saveOfflineEvent(event);
                    successCount++;
                } catch (Exception e) {
                    log.warn("批量上传事件失败: eventUuid={}, err={}", item.getEventUuid(), e.getMessage());
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", request.getEvents() != null ? request.getEvents().size() : 0);
        result.put("success", successCount);
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

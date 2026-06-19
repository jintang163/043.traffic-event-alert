package com.traffic.alert.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.EdgeNodeHeartbeatRequest;
import com.traffic.alert.dto.EdgeNodeQuery;
import com.traffic.alert.dto.EdgeNodeRegisterRequest;
import com.traffic.alert.dto.EdgeEventUploadRequest;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.entity.EdgeNode;
import com.traffic.alert.entity.EdgeNodeHeartbeat;
import com.traffic.alert.entity.EdgeOfflineEvent;
import com.traffic.alert.mapper.AlertEventMapper;
import com.traffic.alert.mapper.EdgeNodeHeartbeatMapper;
import com.traffic.alert.mapper.EdgeNodeMapper;
import com.traffic.alert.mapper.EdgeOfflineEventMapper;
import com.traffic.alert.websocket.AlertWebSocket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EdgeNodeService {

    private final EdgeNodeMapper edgeNodeMapper;
    private final EdgeNodeHeartbeatMapper edgeNodeHeartbeatMapper;
    private final EdgeOfflineEventMapper edgeOfflineEventMapper;
    private final AlertEventMapper alertEventMapper;
    private final CameraService cameraService;
    private final MinioService minioService;
    private final NotificationService notificationService;

    private static final DateTimeFormatter EVENT_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public EdgeNode getById(Long id) {
        return edgeNodeMapper.selectById(id);
    }

    public PageResult<EdgeNode> page(EdgeNodeQuery query) {
        Page<EdgeNode> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<EdgeNode> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(EdgeNode::getNodeName, query.getKeyword())
                    .or().like(EdgeNode::getNodeCode, query.getKeyword());
        }
        if (query.getStatus() != null) {
            wrapper.eq(EdgeNode::getStatus, query.getStatus());
        }
        if (query.getOnlineStatus() != null) {
            wrapper.eq(EdgeNode::getOnlineStatus, query.getOnlineStatus());
        }
        if (query.getDeptId() != null) {
            wrapper.eq(EdgeNode::getDeptId, query.getDeptId());
        }
        wrapper.orderByDesc(EdgeNode::getCreateTime);
        edgeNodeMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), query.getSize());
    }

    public List<EdgeNode> list() {
        return edgeNodeMapper.selectList(new LambdaQueryWrapper<EdgeNode>()
                .eq(EdgeNode::getStatus, 1)
                .orderByAsc(EdgeNode::getNodeName));
    }

    @Transactional
    public EdgeNode save(EdgeNode edgeNode) {
        if (edgeNode.getId() == null) {
            EdgeNode exist = edgeNodeMapper.selectOne(new LambdaQueryWrapper<EdgeNode>()
                    .eq(EdgeNode::getNodeCode, edgeNode.getNodeCode()));
            if (exist != null) {
                throw new BusinessException("节点编码已存在");
            }
            if (edgeNode.getStatus() == null) {
                edgeNode.setStatus(1);
            }
            if (edgeNode.getOnlineStatus() == null) {
                edgeNode.setOnlineStatus(0);
            }
            edgeNodeMapper.insert(edgeNode);
        } else {
            edgeNodeMapper.updateById(edgeNode);
        }
        return edgeNode;
    }

    @Transactional
    public void delete(Long id) {
        edgeNodeMapper.deleteById(id);
        edgeNodeHeartbeatMapper.delete(new LambdaQueryWrapper<EdgeNodeHeartbeat>()
                .eq(EdgeNodeHeartbeat::getEdgeNodeId, id));
        edgeOfflineEventMapper.delete(new LambdaQueryWrapper<EdgeOfflineEvent>()
                .eq(EdgeOfflineEvent::getEdgeNodeId, id));
    }

    @Transactional
    public EdgeNode register(EdgeNodeRegisterRequest request) {
        if (request.getNodeCode() == null || request.getNodeCode().isEmpty()) {
            throw new BusinessException("节点编码不能为空");
        }
        EdgeNode edgeNode = edgeNodeMapper.selectOne(new LambdaQueryWrapper<EdgeNode>()
                .eq(EdgeNode::getNodeCode, request.getNodeCode()));
        if (edgeNode == null) {
            edgeNode = new EdgeNode();
            edgeNode.setNodeCode(request.getNodeCode());
            edgeNode.setNodeName(request.getNodeName() != null ? request.getNodeName() : request.getNodeCode());
            edgeNode.setStatus(1);
            edgeNode.setOnlineStatus(1);
            edgeNode.setLastHeartbeat(LocalDateTime.now());
        }
        edgeNode.setHardwareModel(request.getHardwareModel());
        edgeNode.setGpuInfo(request.getGpuInfo());
        edgeNode.setCpuCores(request.getCpuCores());
        edgeNode.setMemoryGB(request.getMemoryGB());
        edgeNode.setStorageGB(request.getStorageGB());
        edgeNode.setOsInfo(request.getOsInfo());
        edgeNode.setIpAddress(request.getIpAddress());
        edgeNode.setMacAddress(request.getMacAddress());
        edgeNode.setLongitude(request.getLongitude());
        edgeNode.setLatitude(request.getLatitude());
        edgeNode.setLocation(request.getLocation());
        edgeNode.setHeartbeatInterval(request.getHeartbeatInterval());
        edgeNode.setCameraCount(request.getCameraCount());
        if (edgeNode.getId() == null) {
            edgeNodeMapper.insert(edgeNode);
        } else {
            edgeNode.setOnlineStatus(1);
            edgeNode.setLastHeartbeat(LocalDateTime.now());
            edgeNodeMapper.updateById(edgeNode);
        }
        log.info("边缘节点注册成功: nodeCode={}, nodeName={}, id={}",
                edgeNode.getNodeCode(), edgeNode.getNodeName(), edgeNode.getId());
        return edgeNode;
    }

    @Transactional
    public Map<String, Object> heartbeat(EdgeNodeHeartbeatRequest request) {
        if (request.getNodeCode() == null || request.getNodeCode().isEmpty()) {
            throw new BusinessException("节点编码不能为空");
        }
        EdgeNode edgeNode = edgeNodeMapper.selectOne(new LambdaQueryWrapper<EdgeNode>()
                .eq(EdgeNode::getNodeCode, request.getNodeCode()));
        if (edgeNode == null) {
            throw new BusinessException("节点不存在，请先注册");
        }
        edgeNode.setOnlineStatus(1);
        edgeNode.setLastHeartbeat(LocalDateTime.now());
        edgeNode.setCpuUsage(request.getCpuUsage());
        edgeNode.setMemoryUsage(request.getMemoryUsage());
        edgeNode.setGpuUsage(request.getGpuUsage());
        edgeNode.setTemperature(request.getTemperature());
        if (request.getEventCountToday() != null) {
            edgeNode.setEventCountToday(request.getEventCountToday());
        }
        edgeNodeMapper.updateById(edgeNode);

        EdgeNodeHeartbeat heartbeat = new EdgeNodeHeartbeat();
        heartbeat.setEdgeNodeId(edgeNode.getId());
        heartbeat.setNodeCode(request.getNodeCode());
        heartbeat.setCpuUsage(request.getCpuUsage());
        heartbeat.setMemoryUsage(request.getMemoryUsage());
        heartbeat.setGpuUsage(request.getGpuUsage());
        heartbeat.setTemperature(request.getTemperature());
        heartbeat.setNetworkStatus(request.getNetworkStatus());
        heartbeat.setDiskUsage(request.getDiskUsage());
        heartbeat.setProcessCount(request.getProcessCount());
        heartbeat.setCameraOnlineCount(request.getCameraOnlineCount());
        heartbeat.setEventQueueSize(request.getEventQueueSize());
        heartbeat.setExtraInfo(request.getExtraInfo());
        edgeNodeHeartbeatMapper.insert(heartbeat);

        Map<String, Object> result = new HashMap<>();
        result.put("serverTime", LocalDateTime.now());
        result.put("nodeId", edgeNode.getId());
        result.put("nodeCode", edgeNode.getNodeCode());
        result.put("config", getNodeConfig(edgeNode.getId()));
        return result;
    }

    @Transactional
    public void checkOnlineStatus() {
        log.info("开始检查边缘节点在线状态...");
        List<EdgeNode> nodes = edgeNodeMapper.selectList(new LambdaQueryWrapper<EdgeNode>()
                .eq(EdgeNode::getStatus, 1));
        int offlineCount = 0;
        for (EdgeNode node : nodes) {
            if (node.getLastHeartbeat() == null) {
                if (node.getOnlineStatus() == null || node.getOnlineStatus() != 0) {
                    node.setOnlineStatus(0);
                    edgeNodeMapper.updateById(node);
                    offlineCount++;
                    log.warn("边缘节点[{}]无心跳记录，标记为离线", node.getNodeCode());
                }
                continue;
            }
            int interval = node.getHeartbeatInterval() != null ? node.getHeartbeatInterval() : 30;
            LocalDateTime threshold = LocalDateTime.now().minusSeconds((long) interval * 3);
            if (node.getLastHeartbeat().isBefore(threshold)) {
                if (node.getOnlineStatus() == null || node.getOnlineStatus() != 0) {
                    node.setOnlineStatus(0);
                    edgeNodeMapper.updateById(node);
                    offlineCount++;
                    log.warn("边缘节点[{}]超过{}秒未上报心跳，标记为离线", node.getNodeCode(), interval * 3);
                }
            }
        }
        log.info("边缘节点在线状态检查完成，本次标记离线节点数: {}", offlineCount);
    }

    public Map<String, Object> getStatistics() {
        Long total = edgeNodeMapper.selectCount(new LambdaQueryWrapper<EdgeNode>());
        Long online = edgeNodeMapper.selectCount(new LambdaQueryWrapper<EdgeNode>()
                .eq(EdgeNode::getOnlineStatus, 1));
        Long offline = total - online;
        Long enabled = edgeNodeMapper.selectCount(new LambdaQueryWrapper<EdgeNode>()
                .eq(EdgeNode::getStatus, 1));

        LambdaQueryWrapper<EdgeOfflineEvent> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(EdgeOfflineEvent::getCreateTime, LocalDateTime.now().toLocalDate().atStartOfDay());
        Long todayEvents = edgeOfflineEventMapper.selectCount(todayWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("online", online);
        result.put("offline", offline);
        result.put("enabled", enabled);
        result.put("todayEvents", todayEvents);
        return result;
    }

    public Map<String, Object> getNodeConfig(Long nodeId) {
        EdgeNode node = getById(nodeId);
        if (node == null) {
            throw new BusinessException("节点不存在");
        }
        Map<String, Object> config = new HashMap<>();
        if (node.getConfigJson() != null && !node.getConfigJson().isEmpty()) {
            try {
                config = JSON.parseObject(node.getConfigJson(), Map.class);
            } catch (Exception e) {
                log.warn("解析节点配置JSON失败: nodeId={}, err={}", nodeId, e.getMessage());
            }
        }
        config.putIfAbsent("heartbeatInterval", node.getHeartbeatInterval() != null ? node.getHeartbeatInterval() : 30);
        config.putIfAbsent("eventUploadEnabled", true);
        config.putIfAbsent("eventUploadBatchSize", 10);
        config.putIfAbsent("aiDetectionEnabled", true);
        config.putIfAbsent("videoUploadEnabled", true);
        config.putIfAbsent("maxEventQueueSize", 1000);
        config.putIfAbsent("logLevel", "INFO");
        return config;
    }

    @Transactional
    public void updateNodeConfig(Long nodeId, Map<String, Object> config) {
        EdgeNode node = getById(nodeId);
        if (node == null) {
            throw new BusinessException("节点不存在");
        }
        node.setConfigJson(JSON.toJSONString(config));
        edgeNodeMapper.updateById(node);
        log.info("边缘节点配置已更新: nodeId={}", nodeId);
    }

    public List<EdgeOfflineEvent> listOfflineEvents(Long nodeId, Integer uploadStatus) {
        LambdaQueryWrapper<EdgeOfflineEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EdgeOfflineEvent::getEdgeNodeId, nodeId);
        if (uploadStatus != null) {
            wrapper.eq(EdgeOfflineEvent::getUploadStatus, uploadStatus);
        }
        wrapper.orderByDesc(EdgeOfflineEvent::getEventTime);
        return edgeOfflineEventMapper.selectList(wrapper);
    }

    @Transactional
    public AlertEvent processEdgeEvent(EdgeEventUploadRequest request,
                                        MultipartFile snapshotFile, MultipartFile videoFile) {
        EdgeNode node = edgeNodeMapper.selectOne(new LambdaQueryWrapper<EdgeNode>()
                .eq(EdgeNode::getNodeCode, request.getNodeCode()));
        if (node == null) {
            throw new BusinessException("边缘节点不存在: " + request.getNodeCode());
        }

        EdgeOfflineEvent existEvent = edgeOfflineEventMapper.selectOne(new LambdaQueryWrapper<EdgeOfflineEvent>()
                .eq(EdgeOfflineEvent::getEventUuid, request.getEventUuid()));
        if (existEvent != null && existEvent.getAlertEventId() != null) {
            log.info("边缘事件已处理，跳过: eventUuid={}, alertEventId={}",
                    request.getEventUuid(), existEvent.getAlertEventId());
            return alertEventMapper.selectById(existEvent.getAlertEventId());
        }

        Camera camera = resolveCamera(request);

        String eventNo = "EDG" + LocalDateTime.now().format(EVENT_NO_FORMATTER) +
                String.format("%04d", (int) (Math.random() * 10000));

        AlertEvent event = new AlertEvent();
        event.setEventNo(eventNo);
        event.setEventType(request.getEventType());
        event.setEventLevel(request.getEventLevel() != null ? request.getEventLevel() : 1);
        event.setSourceNodeCode(request.getNodeCode());
        event.setAlertStatus(0);
        event.setIsFalsePositive(0);

        if (camera != null) {
            event.setCameraId(camera.getId());
            event.setCameraName(camera.getCameraName());
            event.setLocation(request.getLocation() != null ? request.getLocation() : camera.getLocation());
            event.setLongitude(request.getLongitude() != null ? request.getLongitude() : camera.getLongitude());
            event.setLatitude(request.getLatitude() != null ? request.getLatitude() : camera.getLatitude());
        } else {
            event.setCameraName(request.getCameraName());
            event.setLocation(request.getLocation());
            event.setLongitude(request.getLongitude());
            event.setLatitude(request.getLatitude());
        }

        event.setEventTime(request.getEventTime() != null ? request.getEventTime() : LocalDateTime.now());
        event.setConfidence(request.getConfidence() != null ?
                request.getConfidence().setScale(4, RoundingMode.HALF_UP) : BigDecimal.valueOf(0.9));
        event.setDescription(request.getDescription());

        String snapshotUrl = uploadSnapshotFile(eventNo, snapshotFile);
        if (snapshotUrl != null) {
            event.setEventSnapshot(snapshotUrl);
        }

        String videoUrl = uploadVideoFile(eventNo, videoFile);
        if (videoUrl != null) {
            event.setEventVideo(videoUrl);
        }

        if (request.getEventData() != null && !request.getEventData().isEmpty()) {
            try {
                Map<String, Object> dataMap = JSON.parseObject(request.getEventData(), Map.class);
                if (event.getDescription() == null || event.getDescription().isEmpty()) {
                    Object desc = dataMap.get("description");
                    if (desc != null) event.setDescription(desc.toString());
                }
                if (event.getConfidence() == null || event.getConfidence().compareTo(BigDecimal.ZERO) == 0) {
                    Object conf = dataMap.get("confidence");
                    if (conf != null) event.setConfidence(new BigDecimal(conf.toString()).setScale(4, RoundingMode.HALF_UP));
                }
            } catch (Exception e) {
                log.debug("Failed to parse event data JSON: {}", e.getMessage());
            }
        }

        alertEventMapper.insert(event);
        log.info("边缘事件写入告警主链路: eventNo={}, eventType={}, nodeCode={}, camera={}",
                eventNo, request.getEventType(), request.getNodeCode(), event.getCameraName());

        saveOrUpdateOfflineEvent(request, node, event, snapshotUrl, videoUrl);

        AlertWebSocket.sendAlertMessage(event, false);
        notificationService.sendAlertNotification(event, false);

        return event;
    }

    private Camera resolveCamera(EdgeEventUploadRequest request) {
        if (request.getCameraId() != null) {
            return cameraService.getById(request.getCameraId());
        }
        if (request.getCameraCode() != null && !request.getCameraCode().isEmpty()) {
            List<Camera> cameras = cameraService.list();
            return cameras.stream()
                    .filter(c -> request.getCameraCode().equals(c.getCameraCode()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private String uploadSnapshotFile(String eventNo, MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            String objectName = "edge-snapshots/" + eventNo + "_" + System.currentTimeMillis() + ".jpg";
            return minioService.uploadFile(objectName, file);
        } catch (Exception e) {
            log.error("边缘事件截图上传MinIO失败: eventNo={}, err={}", eventNo, e.getMessage());
            return null;
        }
    }

    private String uploadVideoFile(String eventNo, MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            String fileName = file.getOriginalFilename();
            String ext = (fileName != null && fileName.contains("."))
                    ? fileName.substring(fileName.lastIndexOf(".")) : ".mp4";
            String objectName = "edge-videos/" + eventNo + "_" + System.currentTimeMillis() + ext;
            String contentType = file.getContentType() != null ? file.getContentType() : "video/mp4";
            return minioService.uploadFile(objectName, file.getInputStream(), file.getSize(), contentType);
        } catch (Exception e) {
            log.error("边缘事件视频上传MinIO失败: eventNo={}, err={}", eventNo, e.getMessage());
            return null;
        }
    }

    private void saveOrUpdateOfflineEvent(EdgeEventUploadRequest request, EdgeNode node,
                                            AlertEvent alertEvent, String snapshotUrl, String videoUrl) {
        EdgeOfflineEvent existEvent = edgeOfflineEventMapper.selectOne(new LambdaQueryWrapper<EdgeOfflineEvent>()
                .eq(EdgeOfflineEvent::getEventUuid, request.getEventUuid()));
        if (existEvent != null) {
            existEvent.setAlertEventId(alertEvent.getId());
            existEvent.setUploadStatus(2);
            existEvent.setUploadTime(LocalDateTime.now());
            if (snapshotUrl != null) existEvent.setSnapshotUrl(snapshotUrl);
            if (videoUrl != null) existEvent.setVideoUrl(videoUrl);
            edgeOfflineEventMapper.updateById(existEvent);
        } else {
            EdgeOfflineEvent offlineEvent = new EdgeOfflineEvent();
            offlineEvent.setEdgeNodeId(node.getId());
            offlineEvent.setNodeCode(request.getNodeCode());
            offlineEvent.setEventUuid(request.getEventUuid());
            offlineEvent.setAlertEventId(alertEvent.getId());
            offlineEvent.setEventType(request.getEventType());
            offlineEvent.setEventData(request.getEventData());
            offlineEvent.setEventTime(request.getEventTime() != null ? request.getEventTime() : LocalDateTime.now());
            offlineEvent.setSnapshotUrl(snapshotUrl);
            offlineEvent.setVideoUrl(videoUrl);
            offlineEvent.setUploadStatus(2);
            offlineEvent.setRetryCount(0);
            offlineEvent.setMaxRetry(3);
            offlineEvent.setUploadTime(LocalDateTime.now());
            edgeOfflineEventMapper.insert(offlineEvent);
        }
    }

    @Transactional
    public void saveOfflineEvent(EdgeOfflineEvent event) {
        EdgeOfflineEvent exist = edgeOfflineEventMapper.selectOne(new LambdaQueryWrapper<EdgeOfflineEvent>()
                .eq(EdgeOfflineEvent::getEventUuid, event.getEventUuid()));
        if (exist != null) {
            return;
        }
        EdgeNode node = edgeNodeMapper.selectOne(new LambdaQueryWrapper<EdgeNode>()
                .eq(EdgeNode::getNodeCode, event.getNodeCode()));
        if (node != null) {
            event.setEdgeNodeId(node.getId());
        }
        edgeOfflineEventMapper.insert(event);
    }

    @Transactional
    public void ackOfflineEvents(String nodeCode, List<String> eventUuids) {
        if (eventUuids == null || eventUuids.isEmpty()) {
            return;
        }
        for (String uuid : eventUuids) {
            EdgeOfflineEvent event = edgeOfflineEventMapper.selectOne(new LambdaQueryWrapper<EdgeOfflineEvent>()
                    .eq(EdgeOfflineEvent::getEventUuid, uuid)
                    .eq(EdgeOfflineEvent::getNodeCode, nodeCode));
            if (event != null) {
                event.setUploadStatus(2);
                event.setUploadTime(LocalDateTime.now());
                edgeOfflineEventMapper.updateById(event);
            }
        }
        log.info("边缘事件确认完成: nodeCode={}, count={}", nodeCode, eventUuids.size());
    }

    @Transactional
    public int reprocessUnlinkedOfflineEvents() {
        LambdaQueryWrapper<EdgeOfflineEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(EdgeOfflineEvent::getAlertEventId)
                .eq(EdgeOfflineEvent::getUploadStatus, 2);
        List<EdgeOfflineEvent> unlinked = edgeOfflineEventMapper.selectList(wrapper);
        int count = 0;
        for (EdgeOfflineEvent oe : unlinked) {
            try {
                AlertEvent alertEvent = processEdgeEventFromOffline(oe);
                if (alertEvent != null) {
                    oe.setAlertEventId(alertEvent.getId());
                    edgeOfflineEventMapper.updateById(oe);
                    count++;
                }
            } catch (Exception e) {
                log.warn("补关联离线事件失败: uuid={}, err={}", oe.getEventUuid(), e.getMessage());
            }
        }
        if (count > 0) {
            log.info("补关联离线事件完成: count={}", count);
        }
        return count;
    }

    private AlertEvent processEdgeEventFromOffline(EdgeOfflineEvent oe) {
        String eventNo = "EDG" + LocalDateTime.now().format(EVENT_NO_FORMATTER) +
                String.format("%04d", (int) (Math.random() * 10000));
        AlertEvent event = new AlertEvent();
        event.setEventNo(eventNo);
        event.setEventType(oe.getEventType());
        event.setEventLevel(1);
        event.setSourceNodeCode(oe.getNodeCode());
        event.setAlertStatus(0);
        event.setIsFalsePositive(0);
        event.setEventTime(oe.getEventTime() != null ? oe.getEventTime() : LocalDateTime.now());
        event.setConfidence(BigDecimal.valueOf(0.9));
        event.setEventSnapshot(oe.getSnapshotUrl());
        event.setEventVideo(oe.getVideoUrl());

        if (oe.getEventData() != null && !oe.getEventData().isEmpty()) {
            try {
                Map<String, Object> dataMap = JSON.parseObject(oe.getEventData(), Map.class);
                Object cameraId = dataMap.get("cameraId");
                if (cameraId != null) {
                    Camera camera = cameraService.getById(Long.valueOf(cameraId.toString()));
                    if (camera != null) {
                        event.setCameraId(camera.getId());
                        event.setCameraName(camera.getCameraName());
                        event.setLocation(camera.getLocation());
                        event.setLongitude(camera.getLongitude());
                        event.setLatitude(camera.getLatitude());
                    }
                }
                Object cameraName = dataMap.get("cameraName");
                if (cameraName != null && event.getCameraName() == null) {
                    event.setCameraName(cameraName.toString());
                }
                Object desc = dataMap.get("description");
                if (desc != null) event.setDescription(desc.toString());
                Object conf = dataMap.get("confidence");
                if (conf != null) event.setConfidence(new BigDecimal(conf.toString()).setScale(4, RoundingMode.HALF_UP));
            } catch (Exception e) {
                log.debug("Parse offline event data error: {}", e.getMessage());
            }
        }

        alertEventMapper.insert(event);
        AlertWebSocket.sendAlertMessage(event, false);
        notificationService.sendAlertNotification(event, false);
        return event;
    }
}

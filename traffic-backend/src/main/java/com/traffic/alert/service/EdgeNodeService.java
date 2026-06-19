package com.traffic.alert.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.EdgeNodeHeartbeatRequest;
import com.traffic.alert.dto.EdgeNodeQuery;
import com.traffic.alert.dto.EdgeNodeRegisterRequest;
import com.traffic.alert.entity.EdgeNode;
import com.traffic.alert.entity.EdgeNodeHeartbeat;
import com.traffic.alert.entity.EdgeOfflineEvent;
import com.traffic.alert.mapper.EdgeNodeHeartbeatMapper;
import com.traffic.alert.mapper.EdgeNodeMapper;
import com.traffic.alert.mapper.EdgeOfflineEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
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
    }
}

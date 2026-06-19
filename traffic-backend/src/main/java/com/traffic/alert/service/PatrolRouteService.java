package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.dto.PatrolRouteSaveRequest;
import com.traffic.alert.entity.PatrolExecutionLog;
import com.traffic.alert.entity.PatrolRoute;
import com.traffic.alert.entity.PatrolRoutePoint;
import com.traffic.alert.mapper.PatrolExecutionLogMapper;
import com.traffic.alert.mapper.PatrolRouteMapper;
import com.traffic.alert.mapper.PatrolRoutePointMapper;
import com.traffic.alert.vo.PatrolRouteVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatrolRouteService {

    private final PatrolRouteMapper patrolRouteMapper;
    private final PatrolRoutePointMapper patrolRoutePointMapper;
    private final PatrolExecutionLogMapper patrolExecutionLogMapper;

    public Page<PatrolRoute> page(int current, int size, String keyword) {
        LambdaQueryWrapper<PatrolRoute> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(PatrolRoute::getRouteName, keyword)
                    .or()
                    .like(PatrolRoute::getRouteCode, keyword);
        }
        wrapper.orderByDesc(PatrolRoute::getCreateTime);
        return patrolRouteMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public List<PatrolRoute> list() {
        return patrolRouteMapper.selectList(new LambdaQueryWrapper<PatrolRoute>()
                .eq(PatrolRoute::getStatus, 1)
                .orderByDesc(PatrolRoute::getCreateTime));
    }

    public PatrolRouteVO getDetail(Long id) {
        PatrolRoute route = patrolRouteMapper.selectById(id);
        if (route == null) {
            throw new BusinessException("巡逻路线不存在");
        }
        PatrolRouteVO vo = new PatrolRouteVO();
        BeanUtils.copyProperties(route, vo);
        List<PatrolRoutePoint> points = patrolRoutePointMapper.listByRouteId(id);
        vo.setPoints(points);
        return vo;
    }

    @Transactional
    public PatrolRoute save(PatrolRouteSaveRequest request) {
        boolean isNew = request.getId() == null;
        PatrolRoute route;

        if (isNew) {
            route = new PatrolRoute();
            route.setStatus(1);
        } else {
            route = patrolRouteMapper.selectById(request.getId());
            if (route == null) {
                throw new BusinessException("巡逻路线不存在");
            }
            patrolRoutePointMapper.deleteByRouteId(route.getId());
        }

        route.setRouteName(request.getRouteName());
        route.setRouteCode(request.getRouteCode());
        route.setDescription(request.getDescription());
        route.setStaySeconds(request.getStaySeconds() != null ? request.getStaySeconds() : 30);
        route.setLoopMode(request.getLoopMode() != null ? request.getLoopMode() : 0);

        if (isNew) {
            patrolRouteMapper.insert(route);
            log.info("新增巡逻路线: id={}, name={}", route.getId(), route.getRouteName());
        } else {
            patrolRouteMapper.updateById(route);
            log.info("更新巡逻路线: id={}, name={}", route.getId(), route.getRouteName());
        }

        if (request.getPoints() != null && !request.getPoints().isEmpty()) {
            int sortOrder = 0;
            for (PatrolRouteSaveRequest.PatrolRoutePointDTO pointDTO : request.getPoints()) {
                PatrolRoutePoint point = new PatrolRoutePoint();
                point.setRouteId(route.getId());
                point.setCameraId(pointDTO.getCameraId());
                point.setCameraName(pointDTO.getCameraName());
                point.setCameraCode(pointDTO.getCameraCode());
                point.setSortOrder(sortOrder++);
                point.setStaySeconds(pointDTO.getStaySeconds() != null ? pointDTO.getStaySeconds() : route.getStaySeconds());
                point.setLongitude(pointDTO.getLongitude());
                point.setLatitude(pointDTO.getLatitude());
                point.setLocation(pointDTO.getLocation());
                patrolRoutePointMapper.insert(point);
            }
            log.info("巡逻路线点位保存完成: routeId={}, pointCount={}", route.getId(), request.getPoints().size());
        }

        return route;
    }

    @Transactional
    public void delete(Long id) {
        PatrolRoute route = patrolRouteMapper.selectById(id);
        if (route == null) {
            throw new BusinessException("巡逻路线不存在");
        }
        patrolRoutePointMapper.deleteByRouteId(id);
        patrolRouteMapper.deleteById(id);
        log.info("删除巡逻路线: id={}, name={}", id, route.getRouteName());
    }

    public Long startExecution(Long routeId, Long userId, String userName, Integer loopMode, Integer staySeconds) {
        PatrolRoute route = patrolRouteMapper.selectById(routeId);
        if (route == null) {
            throw new BusinessException("巡逻路线不存在");
        }
        List<PatrolRoutePoint> points = patrolRoutePointMapper.listByRouteId(routeId);
        if (points.isEmpty()) {
            throw new BusinessException("巡逻路线没有点位");
        }

        int effectiveLoopMode = loopMode != null ? loopMode : route.getLoopMode();
        int effectiveStaySeconds = staySeconds != null ? staySeconds : route.getStaySeconds();

        if (staySeconds != null) {
            for (PatrolRoutePoint point : points) {
                point.setStaySeconds(effectiveStaySeconds);
            }
        }

        PatrolExecutionLog log = new PatrolExecutionLog();
        log.setRouteId(routeId);
        log.setRouteName(route.getRouteName());
        log.setStartUserId(userId);
        log.setStartUserName(userName);
        log.setStartTime(LocalDateTime.now());
        log.setExecutionStatus(1);
        log.setTotalPoints(points.size());
        log.setCompletedPoints(0);
        log.setRemark("loopMode=" + effectiveLoopMode + ", staySeconds=" + effectiveStaySeconds);
        patrolExecutionLogMapper.insert(log);

        log.info("开始巡逻执行: routeId={}, logId={}, user={}, loopMode={}, staySeconds={}", routeId, log.getId(), userName, effectiveLoopMode, effectiveStaySeconds);
        return log.getId();
    }

    public void completeExecution(Long logId, String detectedEvents, String remark) {
        PatrolExecutionLog execLog = patrolExecutionLogMapper.selectById(logId);
        if (execLog == null) {
            throw new BusinessException("执行记录不存在");
        }
        execLog.setEndTime(LocalDateTime.now());
        execLog.setExecutionStatus(2);
        execLog.setCompletedPoints(execLog.getTotalPoints());
        execLog.setDetectedEvents(detectedEvents);
        execLog.setRemark(remark);
        patrolExecutionLogMapper.updateById(execLog);
        log.info("完成巡逻执行: logId={}", logId);
    }

    public void updateExecutionProgress(Long logId, int completedPoints, String detectedEvents) {
        PatrolExecutionLog execLog = patrolExecutionLogMapper.selectById(logId);
        if (execLog == null) {
            return;
        }
        execLog.setCompletedPoints(completedPoints);
        execLog.setDetectedEvents(detectedEvents);
        patrolExecutionLogMapper.updateById(execLog);
    }

    public Page<PatrolExecutionLog> listExecutionLogs(int current, int size, Long routeId) {
        LambdaQueryWrapper<PatrolExecutionLog> wrapper = new LambdaQueryWrapper<>();
        if (routeId != null) {
            wrapper.eq(PatrolExecutionLog::getRouteId, routeId);
        }
        wrapper.orderByDesc(PatrolExecutionLog::getCreateTime);
        return patrolExecutionLogMapper.selectPage(new Page<>(current, size), wrapper);
    }
}

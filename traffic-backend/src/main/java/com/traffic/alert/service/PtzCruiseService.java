package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.dto.PtzCruiseRequest;
import com.traffic.alert.entity.PtzCruise;
import com.traffic.alert.entity.PtzCruisePoint;
import com.traffic.alert.entity.PtzPreset;
import com.traffic.alert.mapper.PtzCruiseMapper;
import com.traffic.alert.mapper.PtzCruisePointMapper;
import com.traffic.alert.vo.PtzCruiseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PtzCruiseService {

    private final PtzCruiseMapper ptzCruiseMapper;
    private final PtzCruisePointMapper ptzCruisePointMapper;
    private final PtzPresetService ptzPresetService;
    private final PtzCruiseScheduler ptzCruiseScheduler;

    public List<PtzCruise> listByCamera(Long cameraId) {
        return ptzCruiseMapper.selectList(new LambdaQueryWrapper<PtzCruise>()
                .eq(PtzCruise::getCameraId, cameraId)
                .orderByDesc(PtzCruise::getCreateTime));
    }

    public PtzCruiseVO getDetail(Long id) {
        PtzCruise cruise = ptzCruiseMapper.selectById(id);
        if (cruise == null) {
            throw new BusinessException("巡航路线不存在");
        }
        PtzCruiseVO vo = new PtzCruiseVO();
        BeanUtils.copyProperties(cruise, vo);
        List<PtzCruisePoint> points = ptzCruisePointMapper.selectList(new LambdaQueryWrapper<PtzCruisePoint>()
                .eq(PtzCruisePoint::getCruiseId, id)
                .orderByAsc(PtzCruisePoint::getSortOrder);
        vo.setPoints(points);
        return vo;
    }

    @Transactional
    public PtzCruise save(PtzCruiseRequest request) {
        boolean isNew = request.getId() == null;
        PtzCruise cruise;

        if (isNew) {
            cruise = new PtzCruise();
            cruise.setCameraId(request.getCameraId());
            cruise.setStatus(0);
        } else {
            cruise = ptzCruiseMapper.selectById(request.getId());
            if (cruise == null) {
                throw new BusinessException("巡航路线不存在");
            }
        }

        cruise.setCruiseName(request.getCruiseName());
        cruise.setCruiseType(request.getCruiseType() != null ? request.getCruiseType() : 1);
        cruise.setStaySeconds(request.getStaySeconds() != null ? request.getStaySeconds() : 10);
        cruise.setSpeed(request.getSpeed() != null ? request.getSpeed() : 5);
        cruise.setLoopCount(request.getLoopCount() != null ? request.getLoopCount() : 0);
        cruise.setEventLinkage(request.getEventLinkage() != null ? request.getEventLinkage() : 0);
        cruise.setEventReturnSeconds(request.getEventReturnSeconds() != null ? request.getEventReturnSeconds() : 30);
        cruise.setDescription(request.getDescription());

        if (isNew) {
            ptzCruiseMapper.insert(cruise);
            log.info("新增巡航路线: id={}, name={}", cruise.getId(), cruise.getCruiseName());
        } else {
            ptzCruiseMapper.updateById(cruise);
            log.info("更新巡航路线: id={}, name={}", cruise.getId(), cruise.getCruiseName());
        }

        if (request.getPresetIds() != null) {
            ptzCruisePointMapper.delete(new LambdaQueryWrapper<PtzCruisePoint>()
                    .eq(PtzCruisePoint::getCruiseId, cruise.getId()));

            List<Long> presetIds = request.getPresetIds();
            for (int i = 0; i < presetIds.size(); i++) {
                Long presetId = presetIds.get(i);
                PtzPreset preset = ptzPresetService.getById(presetId);
                if (preset == null) {
                    continue;
                }
                PtzCruisePoint point = new PtzCruisePoint();
                point.setCruiseId(cruise.getId());
                point.setPresetId(presetId);
                point.setPresetIndex(preset.getPresetIndex());
                point.setPresetName(preset.getPresetName());
                point.setStaySeconds(cruise.getStaySeconds());
                point.setSortOrder(i + 1);
                ptzCruisePointMapper.insert(point);
            }
            log.info("更新巡航点: cruiseId={}, 共{}个点", cruise.getId(), presetIds.size());
        }

        return cruise;
    }

    @Transactional
    public void delete(Long id) {
        PtzCruise cruise = ptzCruiseMapper.selectById(id);
        if (cruise == null) {
            throw new BusinessException("巡航路线不存在");
        }
        if (cruise.getStatus() == 1) {
            throw new BusinessException("请先停止巡航再删除");
        }
        ptzCruisePointMapper.delete(new LambdaQueryWrapper<PtzCruisePoint>()
                .eq(PtzCruisePoint::getCruiseId, id));
        ptzCruiseMapper.deleteById(id);
        log.info("删除巡航路线: id={}", id);
    }

    @Transactional
    public void startCruise(Long id) {
        PtzCruiseVO cruise = getDetail(id);
        if (cruise.getPoints() == null || cruise.getPoints().isEmpty()) {
            throw new BusinessException("巡航路线没有巡航点");
        }
        ptzCruiseScheduler.startCruise(cruise);
        PtzCruise update = new PtzCruise();
        update.setId(id);
        update.setStatus(1);
        ptzCruiseMapper.updateById(update);
        log.info("开始巡航: id={}", id);
    }

    @Transactional
    public void stopCruise(Long id) {
        PtzCruise cruise = ptzCruiseMapper.selectById(id);
        if (cruise == null) {
            throw new BusinessException("巡航路线不存在");
        }
        ptzCruiseScheduler.stopCruise(id);
        PtzCruise update = new PtzCruise();
        update.setId(id);
        update.setStatus(0);
        ptzCruiseMapper.updateById(update);
        log.info("停止巡航: id={}", id);
    }

    public boolean isCruising(Long cameraId) {
        return ptzCruiseScheduler.isCruising(cameraId);
    }

    public void pauseCruiseForEvent(Long cameraId) {
        ptzCruiseScheduler.pauseForEvent(cameraId);
    }

    public void resumeCruiseAfterEvent(Long cameraId) {
        ptzCruiseScheduler.resumeAfterEvent(cameraId);
    }

    public void triggerMajorAccidentResponse(Long cameraId, com.traffic.alert.entity.AlertEvent event) {
        pauseCruiseForEvent(cameraId);
        log.info("重大事故PTZ响应已触发: cameraId={}, eventNo={}, severity={}",
                cameraId, event != null ? event.getEventNo() : null,
                event != null ? event.getAccidentSeverity() : null);
    }
}

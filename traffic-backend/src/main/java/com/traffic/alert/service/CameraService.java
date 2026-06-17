package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.CameraQuery;
import com.traffic.alert.dto.PtzControlRequest;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.mapper.CameraMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraMapper cameraMapper;

    public Camera getById(Long id) {
        return cameraMapper.selectById(id);
    }

    public PageResult<Camera> page(CameraQuery query) {
        Page<Camera> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<Camera> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(Camera::getCameraName, query.getKeyword())
                    .or().like(Camera::getCameraCode, query.getKeyword());
        }
        if (query.getProtocol() != null) {
            wrapper.eq(Camera::getProtocol, query.getProtocol());
        }
        if (query.getManufacturer() != null) {
            wrapper.eq(Camera::getManufacturer, query.getManufacturer());
        }
        if (query.getRoadName() != null) {
            wrapper.like(Camera::getRoadName, query.getRoadName());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Camera::getStatus, query.getStatus());
        }
        if (query.getOnlineStatus() != null) {
            wrapper.eq(Camera::getOnlineStatus, query.getOnlineStatus());
        }
        wrapper.orderByDesc(Camera::getCreateTime);
        cameraMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
    }

    public List<Camera> list() {
        return cameraMapper.selectList(new LambdaQueryWrapper<Camera>()
                .eq(Camera::getStatus, 1)
                .orderByAsc(Camera::getRoadName, Camera::getDirection));
    }

    public Camera save(Camera camera) {
        if (camera.getId() == null) {
            cameraMapper.insert(camera);
        } else {
            cameraMapper.updateById(camera);
        }
        return camera;
    }

    public void delete(Long id) {
        cameraMapper.deleteById(id);
    }

    public String getStreamUrl(Long id) {
        Camera camera = getById(id);
        if (camera == null) {
            throw new BusinessException("摄像头不存在");
        }
        return camera.getStreamUrl();
    }

    public Map<String, Object> ptzControl(Long id, PtzControlRequest request) {
        Camera camera = getById(id);
        if (camera == null) {
            throw new BusinessException("摄像头不存在");
        }
        if (camera.getPtzEnabled() == null || camera.getPtzEnabled() != 1) {
            throw new BusinessException("该摄像头不支持云台控制");
        }

        log.info("摄像头[{}]云台控制: command={}, speed={}, presetIndex={}",
                id, request.getCommand(), request.getSpeed(), request.getPresetIndex());

        return Map.of(
                "success", true,
                "cameraId", id,
                "command", request.getCommand(),
                "message", "云台控制指令已发送"
        );
    }

    public Map<String, Object> getStatistics() {
        Long total = cameraMapper.selectCount(new LambdaQueryWrapper<Camera>());
        Long online = cameraMapper.selectCount(new LambdaQueryWrapper<Camera>()
                .eq(Camera::getOnlineStatus, 1));
        Long offline = total - online;
        Long ptzEnabled = cameraMapper.selectCount(new LambdaQueryWrapper<Camera>()
                .eq(Camera::getPtzEnabled, 1));

        return Map.of(
                "total", total,
                "online", online,
                "offline", offline,
                "ptzEnabled", ptzEnabled
        );
    }
}

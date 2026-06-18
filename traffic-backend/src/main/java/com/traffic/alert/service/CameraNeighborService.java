package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.traffic.alert.entity.CameraNeighbor;
import com.traffic.alert.mapper.CameraNeighborMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraNeighborService {

    private final CameraNeighborMapper cameraNeighborMapper;

    public List<CameraNeighbor> listByCameraId(Long cameraId) {
        LambdaQueryWrapper<CameraNeighbor> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CameraNeighbor::getCameraId, cameraId)
                .orderByAsc(CameraNeighbor::getPriority);
        return cameraNeighborMapper.selectList(wrapper);
    }

    public List<Long> listNeighborIds(Long cameraId) {
        return listByCameraId(cameraId).stream()
                .map(CameraNeighbor::getNeighborCameraId)
                .collect(Collectors.toList());
    }

    @Transactional
    public CameraNeighbor addNeighbor(CameraNeighbor neighbor) {
        cameraNeighborMapper.insert(neighbor);
        log.info("添加摄像头相邻关系: cameraId={}, neighborId={}",
                neighbor.getCameraId(), neighbor.getNeighborCameraId());
        return neighbor;
    }

    @Transactional
    public void removeNeighbor(Long id) {
        cameraNeighborMapper.deleteById(id);
        log.info("删除摄像头相邻关系: id={}", id);
    }

    @Transactional
    public void removeByCameraId(Long cameraId) {
        LambdaQueryWrapper<CameraNeighbor> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CameraNeighbor::getCameraId, cameraId);
        cameraNeighborMapper.delete(wrapper);
    }
}

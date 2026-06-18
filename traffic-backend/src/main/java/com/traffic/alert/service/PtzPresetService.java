package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.dto.PtzPresetRequest;
import com.traffic.alert.entity.PtzPreset;
import com.traffic.alert.mapper.PtzPresetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PtzPresetService {

    private final PtzPresetMapper ptzPresetMapper;

    public List<PtzPreset> listByCamera(Long cameraId) {
        return ptzPresetMapper.selectList(new LambdaQueryWrapper<PtzPreset>()
                .eq(PtzPreset::getCameraId, cameraId)
                .orderByAsc(PtzPreset::getSortOrder, PtzPreset::getPresetIndex));
    }

    public PtzPreset getById(Long id) {
        return ptzPresetMapper.selectById(id);
    }

    @Transactional
    public PtzPreset save(PtzPresetRequest request) {
        LambdaQueryWrapper<PtzPreset> wrapper = new LambdaQueryWrapper<PtzPreset>()
                .eq(PtzPreset::getCameraId, request.getCameraId())
                .eq(PtzPreset::getPresetIndex, request.getPresetIndex());
        PtzPreset existing = ptzPresetMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setPresetName(request.getPresetName());
            if (request.getThumbnailUrl() != null) {
                existing.setThumbnailUrl(request.getThumbnailUrl());
            }
            if (request.getSortOrder() != null) {
                existing.setSortOrder(request.getSortOrder());
            }
            ptzPresetMapper.updateById(existing);
            log.info("更新预置位: cameraId={}, presetIndex={}", request.getCameraId(), request.getPresetIndex());
            return existing;
        }

        PtzPreset preset = new PtzPreset();
        preset.setCameraId(request.getCameraId());
        preset.setPresetIndex(request.getPresetIndex());
        preset.setPresetName(request.getPresetName());
        preset.setThumbnailUrl(request.getThumbnailUrl());
        preset.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : request.getPresetIndex());
        ptzPresetMapper.insert(preset);
        log.info("新增预置位: cameraId={}, presetIndex={}, name={}", request.getCameraId(), request.getPresetIndex(), request.getPresetName());
        return preset;
    }

    @Transactional
    public void delete(Long id) {
        PtzPreset preset = ptzPresetMapper.selectById(id);
        if (preset == null) {
            throw new BusinessException("预置位不存在");
        }
        ptzPresetMapper.deleteById(id);
        log.info("删除预置位: id={}", id);
    }

    public Integer getNextPresetIndex(Long cameraId) {
        List<PtzPreset> presets = listByCamera(cameraId);
        if (presets.isEmpty()) {
            return 1;
        }
        int maxIndex = presets.stream().mapToInt(PtzPreset::getPresetIndex).max().orElse(0);
        return maxIndex + 1;
    }

    public PtzPreset findNearestPreset(Long cameraId, String keyword) {
        if (keyword == null || keyword.isEmpty()) return null;
        List<PtzPreset> presets = listByCamera(cameraId);
        for (PtzPreset p : presets) {
            if (p.getPresetName() != null && p.getPresetName().contains(keyword)) {
                return p;
            }
        }
        return null;
    }
}

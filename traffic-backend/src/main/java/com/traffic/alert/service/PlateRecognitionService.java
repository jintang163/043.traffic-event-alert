package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.PlateRecognitionQuery;
import com.traffic.alert.entity.PlateRecognition;
import com.traffic.alert.mapper.PlateRecognitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlateRecognitionService {

    private final PlateRecognitionMapper mapper;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public PlateRecognition getById(Long id) {
        return mapper.selectById(id);
    }

    public List<PlateRecognition> listByAlertEventId(Long alertEventId) {
        return mapper.selectList(new LambdaQueryWrapper<PlateRecognition>()
                .eq(PlateRecognition::getAlertEventId, alertEventId)
                .orderByDesc(PlateRecognition::getRecognizeTime));
    }

    public PageResult<PlateRecognition> page(PlateRecognitionQuery query) {
        Page<PlateRecognition> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<PlateRecognition> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getEventNo())) {
            wrapper.eq(PlateRecognition::getEventNo, query.getEventNo());
        }
        if (StringUtils.hasText(query.getPlateNumber())) {
            wrapper.like(PlateRecognition::getPlateNumber, query.getPlateNumber());
        }
        if (query.getCameraId() != null) {
            wrapper.eq(PlateRecognition::getCameraId, query.getCameraId());
        }
        if (StringUtils.hasText(query.getSceneType())) {
            wrapper.eq(PlateRecognition::getSceneType, query.getSceneType());
        }
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(PlateRecognition::getRecognizeTime, LocalDateTime.parse(query.getStartTime()));
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(PlateRecognition::getRecognizeTime, LocalDateTime.parse(query.getEndTime()));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(PlateRecognition::getPlateNumber, query.getKeyword())
                    .or().like(PlateRecognition::getEventNo, query.getKeyword()));
        }
        wrapper.orderByDesc(PlateRecognition::getRecognizeTime);
        mapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), query.getSize());
    }

    public PlateRecognition save(PlateRecognition entity) {
        if (entity.getId() == null) {
            if (!StringUtils.hasText(entity.getRecognizeNo())) {
                entity.setRecognizeNo("LPR" + LocalDateTime.now().format(FORMATTER)
                        + String.format("%04d", (int) (Math.random() * 10000)));
            }
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return entity;
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    public List<PlateRecognition> listByEventNo(String eventNo) {
        return mapper.selectList(new LambdaQueryWrapper<PlateRecognition>()
                .eq(PlateRecognition::getEventNo, eventNo)
                .orderByDesc(PlateRecognition::getRecognizeTime));
    }
}

package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.OnDutyQuery;
import com.traffic.alert.entity.OnDuty;
import com.traffic.alert.mapper.OnDutyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnDutyService {

    private final OnDutyMapper onDutyMapper;

    public OnDuty getById(Long id) {
        return onDutyMapper.selectById(id);
    }

    public PageResult<OnDuty> page(OnDutyQuery query) {
        Page<OnDuty> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<OnDuty> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            wrapper.eq(OnDuty::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getDutyDate())) {
            wrapper.eq(OnDuty::getDutyDate, LocalDate.parse(query.getDutyDate()));
        }
        if (query.getDutyType() != null) {
            wrapper.eq(OnDuty::getDutyType, query.getDutyType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(OnDuty::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(OnDuty::getDutyDate);
        onDutyMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), query.getSize());
    }

    public List<OnDuty> getCurrentDuty() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalDateTime current = LocalDateTime.now();
        LambdaQueryWrapper<OnDuty> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OnDuty::getDutyDate, today)
                .eq(OnDuty::getStatus, 1)
                .and(w -> {
                    w.eq(OnDuty::getDutyType, 3);
                    w.or(ww -> ww.eq(OnDuty::getDutyType, 1)
                            .le(OnDuty::getStartTime, current)
                            .ge(OnDuty::getEndTime, current));
                    w.or(ww -> ww.eq(OnDuty::getDutyType, 2)
                            .le(OnDuty::getStartTime, current)
                            .ge(OnDuty::getEndTime, current));
                });
        return onDutyMapper.selectList(wrapper);
    }

    public List<OnDuty> getDutyByDate(LocalDate date) {
        return onDutyMapper.selectList(new LambdaQueryWrapper<OnDuty>()
                .eq(OnDuty::getDutyDate, date)
                .eq(OnDuty::getStatus, 1));
    }

    public OnDuty save(OnDuty onDuty) {
        if (onDuty.getId() == null) {
            if (onDuty.getDutyDate() != null && onDuty.getStartTime() == null) {
                LocalDateTime base = onDuty.getDutyDate().atStartOfDay();
                onDuty.setStartTime(base.plusHours(onDuty.getDutyType() == 2 ? 18 : 8));
                onDuty.setEndTime(base.plusHours(onDuty.getDutyType() == 1 ? 18 : 24).plusMinutes(0));
            }
            onDutyMapper.insert(onDuty);
        } else {
            onDutyMapper.updateById(onDuty);
        }
        return onDuty;
    }

    public void delete(Long id) {
        onDutyMapper.deleteById(id);
    }
}

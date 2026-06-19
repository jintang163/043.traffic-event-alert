package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.NotifyRuleQuery;
import com.traffic.alert.entity.NotifyRule;
import com.traffic.alert.mapper.NotifyRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyRuleService {

    private final NotifyRuleMapper notifyRuleMapper;

    public NotifyRule getById(Long id) {
        return notifyRuleMapper.selectById(id);
    }

    public PageResult<NotifyRule> page(NotifyRuleQuery query) {
        Page<NotifyRule> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<NotifyRule> wrapper = new LambdaQueryWrapper<>();
        if (query.getEventType() != null && !query.getEventType().isEmpty()) {
            wrapper.eq(NotifyRule::getEventType, query.getEventType());
        }
        if (query.getEventLevel() != null) {
            wrapper.eq(NotifyRule::getEventLevel, query.getEventLevel());
        }
        if (query.getChannelId() != null) {
            wrapper.eq(NotifyRule::getChannelId, query.getChannelId());
        }
        if (query.getEnabled() != null) {
            wrapper.eq(NotifyRule::getEnabled, query.getEnabled());
        }
        wrapper.orderByAsc(NotifyRule::getPriority).orderByAsc(NotifyRule::getSortOrder);
        notifyRuleMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), query.getSize());
    }

    public List<NotifyRule> findMatchedRules(String eventType, Integer eventLevel) {
        LambdaQueryWrapper<NotifyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotifyRule::getEnabled, 1);
        wrapper.and(w -> {
            w.eq(NotifyRule::getEventType, eventType);
            w.or(ww -> ww.isNull(NotifyRule::getEventType));
        });
        wrapper.and(w -> {
            w.eq(NotifyRule::getEventLevel, eventLevel);
            w.or(ww -> ww.isNull(NotifyRule::getEventLevel));
        });
        wrapper.orderByAsc(NotifyRule::getPriority).orderByAsc(NotifyRule::getSortOrder);
        return notifyRuleMapper.selectList(wrapper);
    }

    public NotifyRule save(NotifyRule rule) {
        if (rule.getId() == null) {
            notifyRuleMapper.insert(rule);
        } else {
            notifyRuleMapper.updateById(rule);
        }
        return rule;
    }

    public void delete(Long id) {
        notifyRuleMapper.deleteById(id);
    }
}

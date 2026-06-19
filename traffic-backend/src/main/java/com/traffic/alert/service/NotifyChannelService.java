package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.NotifyChannelQuery;
import com.traffic.alert.entity.NotifyChannel;
import com.traffic.alert.mapper.NotifyChannelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyChannelService {

    private final NotifyChannelMapper notifyChannelMapper;

    public NotifyChannel getById(Long id) {
        return notifyChannelMapper.selectById(id);
    }

    public List<NotifyChannel> listAll() {
        return notifyChannelMapper.selectList(new LambdaQueryWrapper<NotifyChannel>()
                .orderByAsc(NotifyChannel::getSortOrder));
    }

    public List<NotifyChannel> listEnabled() {
        return notifyChannelMapper.selectList(new LambdaQueryWrapper<NotifyChannel>()
                .eq(NotifyChannel::getEnabled, 1)
                .orderByAsc(NotifyChannel::getSortOrder));
    }

    public PageResult<NotifyChannel> page(NotifyChannelQuery query) {
        Page<NotifyChannel> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<NotifyChannel> wrapper = new LambdaQueryWrapper<>();
        if (query.getChannelType() != null && !query.getChannelType().isEmpty()) {
            wrapper.eq(NotifyChannel::getChannelType, query.getChannelType());
        }
        if (query.getEnabled() != null) {
            wrapper.eq(NotifyChannel::getEnabled, query.getEnabled());
        }
        wrapper.orderByAsc(NotifyChannel::getSortOrder);
        notifyChannelMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), query.getSize());
    }

    public NotifyChannel save(NotifyChannel channel) {
        if (channel.getId() == null) {
            notifyChannelMapper.insert(channel);
        } else {
            notifyChannelMapper.updateById(channel);
        }
        return channel;
    }

    public void delete(Long id) {
        notifyChannelMapper.deleteById(id);
    }

    public NotifyChannel getByCode(String channelCode) {
        return notifyChannelMapper.selectOne(new LambdaQueryWrapper<NotifyChannel>()
                .eq(NotifyChannel::getChannelCode, channelCode));
    }
}

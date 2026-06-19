package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.traffic.alert.entity.PoliceSystemConfig;
import com.traffic.alert.mapper.PoliceSystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PoliceSystemConfigService {

    private final PoliceSystemConfigMapper mapper;

    public PoliceSystemConfig getById(Long id) {
        return mapper.selectById(id);
    }

    public PoliceSystemConfig getByCode(String systemCode) {
        return mapper.selectOne(new LambdaQueryWrapper<PoliceSystemConfig>()
                .eq(PoliceSystemConfig::getSystemCode, systemCode)
                .last("LIMIT 1"));
    }

    public List<PoliceSystemConfig> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<PoliceSystemConfig>()
                .orderByAsc(PoliceSystemConfig::getId));
    }

    public List<PoliceSystemConfig> listEnabled() {
        return mapper.selectList(new LambdaQueryWrapper<PoliceSystemConfig>()
                .eq(PoliceSystemConfig::getEnabled, 1)
                .orderByAsc(PoliceSystemConfig::getId));
    }

    public PoliceSystemConfig save(PoliceSystemConfig entity) {
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return entity;
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}

package com.traffic.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.alert.entity.GeoFence;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GeoFenceMapper extends BaseMapper<GeoFence> {
}

package com.traffic.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.alert.entity.PatrolRoutePoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PatrolRoutePointMapper extends BaseMapper<PatrolRoutePoint> {

    @Select("SELECT * FROM patrol_route_point WHERE route_id = #{routeId} ORDER BY sort_order ASC")
    List<PatrolRoutePoint> listByRouteId(@Param("routeId") Long routeId);

    @Select("DELETE FROM patrol_route_point WHERE route_id = #{routeId}")
    void deleteByRouteId(@Param("routeId") Long routeId);
}

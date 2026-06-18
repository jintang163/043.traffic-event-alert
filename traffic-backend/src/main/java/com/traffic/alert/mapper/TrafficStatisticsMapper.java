package com.traffic.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.alert.entity.TrafficStatistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TrafficStatisticsMapper extends BaseMapper<TrafficStatistics> {

    @Select("SELECT * FROM traffic_statistics WHERE camera_id = #{cameraId} " +
            "AND stat_time >= #{startTime} AND stat_time <= #{endTime} " +
            "AND aggregate_type = #{aggregateType} " +
            "ORDER BY stat_time ASC")
    List<TrafficStatistics> queryByTimeRange(
            @Param("cameraId") Long cameraId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("aggregateType") String aggregateType);

    @Select("SELECT * FROM traffic_statistics WHERE camera_id = #{cameraId} " +
            "AND lane_no = #{laneNo} " +
            "AND stat_time >= #{startTime} AND stat_time <= #{endTime} " +
            "AND aggregate_type = #{aggregateType} " +
            "ORDER BY stat_time ASC")
    List<TrafficStatistics> queryByCameraAndLane(
            @Param("cameraId") Long cameraId,
            @Param("laneNo") Integer laneNo,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("aggregateType") String aggregateType);
}

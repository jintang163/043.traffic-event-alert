package com.traffic.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.alert.entity.EventPrediction;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EventPredictionMapper extends BaseMapper<EventPrediction> {

    @Insert("<script>" +
            "INSERT INTO event_prediction (prediction_no, prediction_time, target_start_time, target_end_time, target_hours, " +
            "camera_id, camera_name, road_name, longitude, latitude, geom, " +
            "risk_score, risk_level, risk_level_label, event_type, event_type_label, " +
            "probability, historical_event_count, weather_factor, time_factor, holiday_factor, " +
            "feature_json, confidence, status, description, create_time) VALUES " +
            "<foreach collection='list' item='p' separator=','>" +
            "(#{p.predictionNo}, #{p.predictionTime}, #{p.targetStartTime}, #{p.targetEndTime}, #{p.targetHours}, " +
            "#{p.cameraId}, #{p.cameraName}, #{p.roadName}, #{p.longitude}, #{p.latitude}, " +
            "ST_GeometryFromText(CONCAT('POINT(', #{p.longitude}, ' ', #{p.latitude}, ')'), 4326), " +
            "#{p.riskScore}, #{p.riskLevel}, #{p.riskLevelLabel}, #{p.eventType}, #{p.eventTypeLabel}, " +
            "#{p.probability}, #{p.historicalEventCount}, #{p.weatherFactor}, #{p.timeFactor}, #{p.holidayFactor}, " +
            "#{p.featureJson}, #{p.confidence}, #{p.status}, #{p.description}, NOW())" +
            "</foreach>" +
            "</script>")
    int batchInsertWithGeom(@Param("list") List<EventPrediction> predictions);

    @Select("SELECT ep.*, ST_AsText(ep.geom) as geomWkt FROM event_prediction ep " +
            "WHERE ep.target_start_time BETWEEN #{startTime} AND #{endTime} " +
            "AND ep.status = 1 AND ep.deleted = 0 " +
            "ORDER BY ep.risk_score DESC")
    List<EventPrediction> selectByTimeRangeWithGeom(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT ep.*, ST_AsText(ep.geom) as geomWkt FROM event_prediction ep " +
            "WHERE ep.prediction_time = (" +
            "    SELECT MAX(prediction_time) FROM event_prediction " +
            "    WHERE status = 1 AND deleted = 0 " +
            "    AND target_start_time BETWEEN #{startTime} AND #{endTime}" +
            ") " +
            "AND ep.status = 1 AND ep.deleted = 0 " +
            "ORDER BY ep.risk_score DESC")
    List<EventPrediction> selectLatestByTimeRangeWithGeom(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM event_prediction " +
            "WHERE camera_id = #{cameraId} " +
            "AND target_start_time >= #{startTime} " +
            "AND status = 1 AND deleted = 0 " +
            "ORDER BY target_start_time DESC " +
            "LIMIT #{limit}")
    List<EventPrediction> selectByCamera(
            @Param("cameraId") Long cameraId,
            @Param("startTime") LocalDateTime startTime,
            @Param("limit") Integer limit);
}

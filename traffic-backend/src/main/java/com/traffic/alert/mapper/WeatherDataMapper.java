package com.traffic.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.alert.entity.WeatherData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WeatherDataMapper extends BaseMapper<WeatherData> {

    @Select("SELECT * FROM weather_data " +
            "WHERE location_code = #{locationCode} " +
            "AND record_time BETWEEN #{startTime} AND #{endTime} " +
            "AND deleted = 0 " +
            "ORDER BY record_time DESC")
    List<WeatherData> selectByLocationAndTimeRange(
            @Param("locationCode") String locationCode,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM weather_data " +
            "WHERE location_code = #{locationCode} " +
            "AND deleted = 0 " +
            "ORDER BY record_time DESC " +
            "LIMIT 1")
    WeatherData selectLatest(@Param("locationCode") String locationCode);

    @Select("SELECT * FROM weather_data " +
            "WHERE record_time = (" +
            "    SELECT MAX(record_time) FROM weather_data WHERE deleted = 0" +
            ") " +
            "AND deleted = 0 " +
            "ORDER BY location_code")
    List<WeatherData> selectAllLatest();

    @Select("SELECT DISTINCT location_code FROM weather_data WHERE deleted = 0")
    List<String> selectAllLocationCodes();
}

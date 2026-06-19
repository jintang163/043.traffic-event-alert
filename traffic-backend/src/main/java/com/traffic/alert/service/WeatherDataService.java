package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.WeatherDataQuery;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.entity.WeatherData;
import com.traffic.alert.mapper.CameraMapper;
import com.traffic.alert.mapper.WeatherDataMapper;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherDataService {

    private final WeatherDataMapper weatherDataMapper;
    private final CameraMapper cameraMapper;
    private final AiEngineService aiEngineService;

    public WeatherData getById(Long id) {
        return weatherDataMapper.selectById(id);
    }

    public WeatherData getLatest(String locationCode) {
        if (locationCode == null || locationCode.isEmpty()) {
            locationCode = "DEFAULT";
        }
        return weatherDataMapper.selectLatest(locationCode);
    }

    public Map<String, Object> getLatestForCamera(Long cameraId) {
        Camera camera = cameraMapper.selectById(cameraId);
        if (camera == null) {
            return Map.of(
                    "weatherType", "NORMAL",
                    "weatherTypeLabel", "正常",
                    "visibility", 15.0,
                    "brightnessFactor", 1.0,
                    "needsEnhancement", false
            );
        }

        WeatherData data = null;
        if (camera.getLocationCode() != null && !camera.getLocationCode().isEmpty()) {
            data = weatherDataMapper.selectLatest(camera.getLocationCode());
        }
        if (data == null) {
            data = weatherDataMapper.selectLatest("DEFAULT");
        }
        if (data == null) {
            return Map.of(
                    "weatherType", "NORMAL",
                    "weatherTypeLabel", "正常",
                    "visibility", 15.0,
                    "brightnessFactor", 1.0,
                    "needsEnhancement", false
            );
        }

        String weatherType = data.getWeatherType();
        boolean needsEnhancement = false;
        double brightnessFactor = 1.0;
        String label = "正常";

        switch (weatherType) {
            case "RAIN":
                needsEnhancement = true;
                brightnessFactor = 1.1;
                label = "雨天";
                break;
            case "SNOW":
                needsEnhancement = true;
                brightnessFactor = 1.2;
                label = "雪天";
                break;
            case "FOG":
            case "HAZE":
                needsEnhancement = true;
                brightnessFactor = 1.3;
                label = weatherType.equals("FOG") ? "雾天" : "霾天";
                break;
            case "CLOUDY":
                label = "多云";
                brightnessFactor = 1.05;
                break;
            case "SUNNY":
                label = "晴天";
                break;
            default:
                label = "正常";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("weatherType", weatherType);
        result.put("weatherTypeLabel", label);
        result.put("temperature", data.getTemperature() != null ? data.getTemperature().doubleValue() : null);
        result.put("humidity", data.getHumidity() != null ? data.getHumidity().doubleValue() : null);
        result.put("visibility", data.getVisibility() != null ? data.getVisibility().doubleValue() : 15.0);
        result.put("windSpeed", data.getWindSpeed() != null ? data.getWindSpeed().doubleValue() : null);
        result.put("precipitation", data.getPrecipitation() != null ? data.getPrecipitation().doubleValue() : null);
        result.put("brightnessFactor", brightnessFactor);
        result.put("needsEnhancement", needsEnhancement);
        result.put("recordTime", data.getRecordTime());
        result.put("locationName", data.getLocationName());

        return result;
    }

    public List<WeatherData> getLatestAll() {
        return weatherDataMapper.selectAllLatest();
    }

    public List<WeatherData> getByLocationAndTimeRange(String locationCode, LocalDateTime startTime, LocalDateTime endTime) {
        return weatherDataMapper.selectByLocationAndTimeRange(locationCode, startTime, endTime);
    }

    public List<String> getAllLocationCodes() {
        return weatherDataMapper.selectAllLocationCodes();
    }

    public PageResult<WeatherData> page(WeatherDataQuery query) {
        Page<WeatherData> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<WeatherData> wrapper = new LambdaQueryWrapper<>();
        if (query.getLocationCode() != null && !query.getLocationCode().isEmpty()) {
            wrapper.eq(WeatherData::getLocationCode, query.getLocationCode());
        }
        if (query.getWeatherType() != null && !query.getWeatherType().isEmpty()) {
            wrapper.eq(WeatherData::getWeatherType, query.getWeatherType());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(WeatherData::getRecordTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(WeatherData::getRecordTime, query.getEndTime());
        }
        wrapper.orderByDesc(WeatherData::getRecordTime);
        weatherDataMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public WeatherData save(WeatherData data) {
        boolean isNew = data.getId() == null;
        if (isNew) {
            if (data.getRecordTime() == null) {
                data.setRecordTime(LocalDateTime.now());
            }
            weatherDataMapper.insert(data);
        } else {
            weatherDataMapper.updateById(data);
        }

        try {
            List<Camera> cameras = cameraMapper.selectList(
                    new LambdaQueryWrapper<Camera>()
                            .eq(Camera::getLocationCode, data.getLocationCode())
                            .eq(Camera::getStatus, 1)
            );
            for (Camera camera : cameras) {
                Map<String, Object> weatherInfo = getLatestForCamera(camera.getId());
                aiEngineService.updateWeatherInfo(camera.getId(), (String) weatherInfo.get("weatherType"));
                log.info("天气数据更新后同步到摄像头[{}]: {}", camera.getId(), JSON.toJSONString(weatherInfo));
            }
        } catch (Exception e) {
            log.warn("天气数据同步到AI引擎失败: {}", e.getMessage());
        }

        return data;
    }

    @Transactional
    public void delete(Long id) {
        weatherDataMapper.deleteById(id);
    }
}

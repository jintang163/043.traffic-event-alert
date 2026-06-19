package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.entity.EventPrediction;
import com.traffic.alert.entity.WeatherData;
import com.traffic.alert.mapper.AlertEventMapper;
import com.traffic.alert.mapper.EventPredictionMapper;
import com.traffic.alert.mapper.WeatherDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPredictionService {

    private final EventPredictionMapper predictionMapper;
    private final WeatherDataMapper weatherDataMapper;
    private final AlertEventMapper alertEventMapper;
    private final CameraService cameraService;

    private static final DateTimeFormatter PREDICTION_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Set<String> HOLIDAYS = new HashSet<>(Arrays.asList(
            "01-01", "01-28", "01-29", "01-30", "01-31", "02-01", "02-02",
            "04-04", "04-05", "04-06", "05-01", "05-02", "05-03", "05-04", "05-05",
            "06-10", "06-11", "06-12", "09-15", "09-16", "09-17",
            "10-01", "10-02", "10-03", "10-04", "10-05", "10-06", "10-07"
    ));

    private static final Map<String, BigDecimal> WEATHER_WEIGHTS = new HashMap<>();
    static {
        WEATHER_WEIGHTS.put("SUNNY", new BigDecimal("0.8"));
        WEATHER_WEIGHTS.put("CLOUDY", new BigDecimal("1.0"));
        WEATHER_WEIGHTS.put("RAIN", new BigDecimal("1.8"));
        WEATHER_WEIGHTS.put("SNOW", new BigDecimal("2.5"));
        WEATHER_WEIGHTS.put("FOG", new BigDecimal("2.2"));
        WEATHER_WEIGHTS.put("HAZE", new BigDecimal("1.5"));
    }

    public EventPrediction getById(Long id) {
        return predictionMapper.selectById(id);
    }

    public PageResult<EventPrediction> page(int current, int size, String status, Integer riskLevel) {
        Page<EventPrediction> page = new Page<>(current, size);
        LambdaQueryWrapper<EventPrediction> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(EventPrediction::getStatus, Integer.parseInt(status));
        }
        if (riskLevel != null) {
            wrapper.eq(EventPrediction::getRiskLevel, riskLevel);
        }
        wrapper.orderByDesc(EventPrediction::getPredictionTime)
                .orderByDesc(EventPrediction::getRiskScore);
        predictionMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), size);
    }

    public List<EventPrediction> getPredictions(LocalDateTime startTime, LocalDateTime endTime) {
        return predictionMapper.selectLatestByTimeRangeWithGeom(startTime, endTime);
    }

    public List<EventPrediction> getPredictionsForNextHour() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = now.plusHours(1);
        return predictionMapper.selectLatestByTimeRangeWithGeom(now, endTime);
    }

    @Transactional
    public List<EventPrediction> generatePredictions(int targetHours) {
        LocalDateTime predictionTime = LocalDateTime.now();
        LocalDateTime targetStart = predictionTime;
        LocalDateTime targetEnd = predictionTime.plusHours(targetHours);

        WeatherData currentWeather = weatherDataMapper.selectLatest("DEFAULT");
        List<Camera> cameras = cameraService.list();
        Map<Long, List<AlertEvent>> historicalEvents = getHistoricalEvents(cameras, targetStart);

        List<EventPrediction> predictions = new ArrayList<>();
        String basePredictionNo = "PRD" + predictionTime.format(PREDICTION_NO_FORMATTER);

        for (int i = 0; i < cameras.size(); i++) {
            Camera camera = cameras.get(i);
            EventPrediction prediction = calculateRisk(
                    camera, historicalEvents.get(camera.getId()),
                    currentWeather, targetStart, targetEnd, targetHours,
                    basePredictionNo, i
            );
            if (prediction != null) {
                predictions.add(prediction);
            }
        }

        if (!predictions.isEmpty()) {
            predictionMapper.batchInsertWithGeom(predictions);
            log.info("生成预测结果 {} 条，时间窗: {} - {}", predictions.size(), targetStart, targetEnd);
        }

        return predictions;
    }

    private EventPrediction calculateRisk(Camera camera, List<AlertEvent> historicalEvents,
                                           WeatherData weather, LocalDateTime targetStart,
                                           LocalDateTime targetEnd, int targetHours,
                                           String baseNo, int index) {
        if (camera.getLongitude() == null || camera.getLatitude() == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        int dayOfWeek = now.getDayOfWeek().getValue();
        int hour = now.getHour();
        boolean isHoliday = isHoliday(now.toLocalDate());
        boolean isWeekend = dayOfWeek >= 6;

        BigDecimal baseScore = new BigDecimal("20");

        BigDecimal historicalFactor = calculateHistoricalFactor(historicalEvents, hour, dayOfWeek, targetHours);
        BigDecimal weatherFactor = calculateWeatherFactor(weather);
        BigDecimal timeFactor = calculateTimeFactor(hour, dayOfWeek, isHoliday, isWeekend);
        BigDecimal holidayFactor = isHoliday ? new BigDecimal("1.3") : new BigDecimal("1.0");

        BigDecimal riskScore = baseScore
                .multiply(historicalFactor)
                .multiply(weatherFactor)
                .multiply(timeFactor)
                .multiply(holidayFactor)
                .setScale(4, RoundingMode.HALF_UP);

        if (riskScore.compareTo(new BigDecimal("100")) > 0) {
            riskScore = new BigDecimal("100");
        }

        int riskLevel = determineRiskLevel(riskScore);
        String riskLevelLabel = getRiskLevelLabel(riskLevel);

        String eventType = predictEventType(historicalEvents, weather, hour);
        String eventTypeLabel = getEventTypeLabel(eventType);

        BigDecimal probability = riskScore.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal confidence = calculateConfidence(historicalEvents, weather);

        EventPrediction prediction = new EventPrediction();
        prediction.setPredictionNo(baseNo + String.format("%03d", index));
        prediction.setPredictionTime(now);
        prediction.setTargetStartTime(targetStart);
        prediction.setTargetEndTime(targetEnd);
        prediction.setTargetHours(targetHours);
        prediction.setCameraId(camera.getId());
        prediction.setCameraName(camera.getCameraName());
        prediction.setRoadName(camera.getRoadName());
        prediction.setLongitude(camera.getLongitude());
        prediction.setLatitude(camera.getLatitude());
        prediction.setRiskScore(riskScore);
        prediction.setRiskLevel(riskLevel);
        prediction.setRiskLevelLabel(riskLevelLabel);
        prediction.setEventType(eventType);
        prediction.setEventTypeLabel(eventTypeLabel);
        prediction.setProbability(probability);
        prediction.setHistoricalEventCount(historicalEvents != null ? historicalEvents.size() : 0);
        prediction.setWeatherFactor(weatherFactor);
        prediction.setTimeFactor(timeFactor);
        prediction.setHolidayFactor(holidayFactor);
        prediction.setConfidence(confidence);
        prediction.setStatus(1);

        Map<String, Object> features = new HashMap<>();
        features.put("hour", hour);
        features.put("dayOfWeek", dayOfWeek);
        features.put("isHoliday", isHoliday);
        features.put("isWeekend", isWeekend);
        features.put("weather", weather != null ? weather.getWeatherType() : "UNKNOWN");
        features.put("temperature", weather != null ? weather.getTemperature() : null);
        features.put("historicalEventCount", historicalEvents != null ? historicalEvents.size() : 0);
        prediction.setFeatureJson(com.alibaba.fastjson2.JSON.toJSONString(features));

        prediction.setDescription(String.format("基于历史数据和当前条件，%s路段未来%d小时内发生%s的概率为%.1f%%",
                camera.getCameraName(), targetHours, eventTypeLabel, probability.multiply(new BigDecimal("100"))));

        return prediction;
    }

    private Map<Long, List<AlertEvent>> getHistoricalEvents(List<Camera> cameras, LocalDateTime targetStart) {
        Map<Long, List<AlertEvent>> result = new HashMap<>();
        LocalDateTime historyStart = targetStart.minusDays(30);
        LocalDateTime historyEnd = targetStart.minusHours(1);

        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AlertEvent::getCameraId, cameras.stream().map(Camera::getId).toList())
                .between(AlertEvent::getEventTime, historyStart, historyEnd)
                .eq(AlertEvent::getIsFalsePositive, 0)
                .eq(AlertEvent::getDeleted, 0);

        List<AlertEvent> events = alertEventMapper.selectList(wrapper);
        for (AlertEvent event : events) {
            result.computeIfAbsent(event.getCameraId(), k -> new ArrayList<>()).add(event);
        }
        return result;
    }

    private BigDecimal calculateHistoricalFactor(List<AlertEvent> events, int currentHour, int dayOfWeek, int targetHours) {
        if (events == null || events.isEmpty()) {
            return new BigDecimal("0.8");
        }

        int matchingEvents = 0;
        for (AlertEvent event : events) {
            LocalDateTime eventTime = event.getEventTime();
            int eventHour = eventTime.getHour();
            int eventDayOfWeek = eventTime.getDayOfWeek().getValue();

            boolean hourMatch = Math.abs(eventHour - currentHour) <= 2;
            boolean dayMatch = (dayOfWeek >= 6 && eventDayOfWeek >= 6)
                    || (dayOfWeek < 6 && eventDayOfWeek < 6);

            if (hourMatch && dayMatch) {
                matchingEvents++;
            }
        }

        double eventsPerHour = (double) matchingEvents / (30.0 * targetHours);
        if (eventsPerHour < 0.01) return new BigDecimal("0.9");
        if (eventsPerHour < 0.05) return new BigDecimal("1.1");
        if (eventsPerHour < 0.1) return new BigDecimal("1.3");
        if (eventsPerHour < 0.2) return new BigDecimal("1.6");
        return new BigDecimal("2.0");
    }

    private BigDecimal calculateWeatherFactor(WeatherData weather) {
        if (weather == null) {
            return new BigDecimal("1.0");
        }
        BigDecimal baseWeight = WEATHER_WEIGHTS.getOrDefault(weather.getWeatherType(), new BigDecimal("1.0"));

        if (weather.getVisibility() != null && weather.getVisibility().compareTo(new BigDecimal("1")) < 0) {
            baseWeight = baseWeight.multiply(new BigDecimal("1.3"));
        }
        if (weather.getWindSpeed() != null && weather.getWindSpeed().compareTo(new BigDecimal("15")) > 0) {
            baseWeight = baseWeight.multiply(new BigDecimal("1.2"));
        }
        if (weather.getTemperature() != null &&
                (weather.getTemperature().compareTo(new BigDecimal("35")) > 0
                        || weather.getTemperature().compareTo(new BigDecimal("-5")) < 0)) {
            baseWeight = baseWeight.multiply(new BigDecimal("1.15"));
        }
        return baseWeight;
    }

    private BigDecimal calculateTimeFactor(int hour, int dayOfWeek, boolean isHoliday, boolean isWeekend) {
        double factor = 1.0;

        if ((hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19)) {
            factor *= 1.6;
        } else if (hour >= 11 && hour <= 13) {
            factor *= 1.2;
        } else if (hour >= 23 || hour <= 5) {
            factor *= 0.7;
        } else {
            factor *= 1.0;
        }

        if (isWeekend || isHoliday) {
            factor *= 0.9;
        }

        return BigDecimal.valueOf(factor);
    }

    private String predictEventType(List<AlertEvent> events, WeatherData weather, int hour) {
        Map<String, Integer> typeCounts = new HashMap<>();
        typeCounts.put("ACCIDENT", 0);
        typeCounts.put("DEBRIS", 0);
        typeCounts.put("CONGESTION", 0);

        if (events != null) {
            for (AlertEvent event : events) {
                String type = event.getEventType();
                typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
            }
        }

        String weatherType = weather != null ? weather.getWeatherType() : "SUNNY";
        if (weatherType.equals("RAIN") || weatherType.equals("SNOW")) {
            typeCounts.put("ACCIDENT", typeCounts.get("ACCIDENT") + 5);
        }
        if (weatherType.equals("FOG") || weatherType.equals("HAZE")) {
            typeCounts.put("CONGESTION", typeCounts.get("CONGESTION") + 3);
            typeCounts.put("ACCIDENT", typeCounts.get("ACCIDENT") + 3);
        }

        if ((hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19)) {
            typeCounts.put("CONGESTION", typeCounts.get("CONGESTION") + 4);
        }

        return typeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("ACCIDENT");
    }

    private BigDecimal calculateConfidence(List<AlertEvent> events, WeatherData weather) {
        int dataPoints = (events != null ? events.size() : 0) + (weather != null ? 1 : 0);
        if (dataPoints >= 50) return new BigDecimal("0.95");
        if (dataPoints >= 30) return new BigDecimal("0.85");
        if (dataPoints >= 10) return new BigDecimal("0.75");
        if (dataPoints >= 5) return new BigDecimal("0.65");
        return new BigDecimal("0.55");
    }

    private int determineRiskLevel(BigDecimal score) {
        if (score.compareTo(new BigDecimal("75")) >= 0) return 4;
        if (score.compareTo(new BigDecimal("50")) >= 0) return 3;
        if (score.compareTo(new BigDecimal("25")) >= 0) return 2;
        return 1;
    }

    private String getRiskLevelLabel(int level) {
        return switch (level) {
            case 1 -> "低风险";
            case 2 -> "中风险";
            case 3 -> "高风险";
            case 4 -> "极高风险";
            default -> "未知";
        };
    }

    private String getEventTypeLabel(String type) {
        return switch (type) {
            case "ACCIDENT" -> "交通事故";
            case "DEBRIS" -> "路面抛洒物";
            case "CONGESTION" -> "交通拥堵";
            default -> "交通事件";
        };
    }

    private boolean isHoliday(LocalDate date) {
        String monthDay = String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth());
        return HOLIDAYS.contains(monthDay);
    }

    public Map<String, Object> getPredictionSummary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now;
        LocalDateTime endTime = now.plusHours(1);

        List<EventPrediction> predictions = predictionMapper.selectLatestByTimeRangeWithGeom(startTime, endTime);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalPoints", predictions.size());
        summary.put("predictionTime", predictions.isEmpty() ? null : predictions.get(0).getPredictionTime());
        summary.put("targetStartTime", predictions.isEmpty() ? null : predictions.get(0).getTargetStartTime());
        summary.put("targetEndTime", predictions.isEmpty() ? null : predictions.get(0).getTargetEndTime());

        int level1 = 0, level2 = 0, level3 = 0, level4 = 0;
        BigDecimal avgScore = BigDecimal.ZERO;
        BigDecimal maxScore = BigDecimal.ZERO;
        EventPrediction highestRisk = null;

        for (EventPrediction p : predictions) {
            avgScore = avgScore.add(p.getRiskScore());
            if (p.getRiskScore().compareTo(maxScore) > 0) {
                maxScore = p.getRiskScore();
                highestRisk = p;
            }
            switch (p.getRiskLevel()) {
                case 1 -> level1++;
                case 2 -> level2++;
                case 3 -> level3++;
                case 4 -> level4++;
            }
        }

        if (!predictions.isEmpty()) {
            avgScore = avgScore.divide(BigDecimal.valueOf(predictions.size()), 2, RoundingMode.HALF_UP);
        }

        summary.put("level1Count", level1);
        summary.put("level2Count", level2);
        summary.put("level3Count", level3);
        summary.put("level4Count", level4);
        summary.put("avgScore", avgScore);
        summary.put("maxScore", maxScore);
        summary.put("highestRisk", highestRisk);

        return summary;
    }
}

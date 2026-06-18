package com.traffic.alert.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.traffic.alert.config.InfluxDBConfig;
import com.traffic.alert.dto.TrafficStatisticsQuery;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.entity.GlobalTrack;
import com.traffic.alert.entity.TrackPoint;
import com.traffic.alert.entity.TrafficStatistics;
import com.traffic.alert.mapper.CameraMapper;
import com.traffic.alert.mapper.GlobalTrackMapper;
import com.traffic.alert.mapper.TrackPointMapper;
import com.traffic.alert.mapper.TrafficStatisticsMapper;
import com.traffic.alert.vo.TrafficRealtimeVO;
import com.traffic.alert.vo.TrafficStatisticsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TrafficStatisticsService {

    @Autowired
    private TrafficStatisticsMapper trafficStatisticsMapper;
    @Autowired
    private TrackPointMapper trackPointMapper;
    @Autowired
    private GlobalTrackMapper globalTrackMapper;
    @Autowired
    private CameraMapper cameraMapper;
    @Autowired(required = false)
    private InfluxDBClient influxDBClient;
    @Autowired(required = false)
    private WriteApiBlocking writeApiBlocking;
    @Autowired
    private InfluxDBConfig influxDBConfig;

    private final Map<Long, Set<Long>> cameraLaneVehicles = new ConcurrentHashMap<>();
    private final Map<String, List<BigDecimal>> cameraLaneSpeeds = new ConcurrentHashMap<>();
    private final Map<String, Integer> cameraLaneOccupancyPoints = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastAggregateTime = new ConcurrentHashMap<>();

    private static final String MEASUREMENT = "traffic_statistics";

    public boolean isInfluxDbAvailable() {
        if (!influxDBConfig.isEnabled() || influxDBClient == null) {
            return false;
        }
        try {
            return influxDBClient.ping();
        } catch (Exception e) {
            return false;
        }
    }

    public List<TrafficStatisticsVO> queryStatistics(TrafficStatisticsQuery query) {
        LocalDateTime startTime = query.getStartTime();
        LocalDateTime endTime = query.getEndTime();
        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(1);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        List<TrafficStatistics> list;
        if (query.getLaneNo() != null) {
            list = trafficStatisticsMapper.queryByCameraAndLane(
                    query.getCameraId(), query.getLaneNo(),
                    startTime, endTime, query.getAggregateType());
        } else {
            list = trafficStatisticsMapper.queryByTimeRange(
                    query.getCameraId(), startTime, endTime, query.getAggregateType());
        }

        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    public List<TrafficStatisticsVO> queryFromInfluxDB(TrafficStatisticsQuery query) {
        if (influxDBClient == null) {
            return queryStatistics(query);
        }

        LocalDateTime startTime = query.getStartTime();
        LocalDateTime endTime = query.getEndTime();
        if (startTime == null) startTime = LocalDateTime.now().minusHours(1);
        if (endTime == null) endTime = LocalDateTime.now();

        String rangeStart = toRFC3339(startTime);
        String rangeStop = toRFC3339(endTime);

        StringBuilder flux = new StringBuilder();
        flux.append(String.format("from(bucket: \"%s\")", influxDBConfig.getBucket()));
        flux.append(String.format(" |> range(start: %s, stop: %s)", rangeStart, rangeStop));
        flux.append(String.format(" |> filter(fn: (r) => r._measurement == \"%s\")", MEASUREMENT));

        if (query.getCameraId() != null) {
            flux.append(String.format(" |> filter(fn: (r) => r.camera_id == \"%s\")", query.getCameraId()));
        }
        if (query.getLaneNo() != null) {
            flux.append(String.format(" |> filter(fn: (r) => r.lane_no == \"%s\")", query.getLaneNo()));
        }
        if (query.getAggregateType() != null && !query.getAggregateType().isEmpty()) {
            flux.append(String.format(" |> filter(fn: (r) => r.aggregate_type == \"%s\")", query.getAggregateType()));
        }

        flux.append(" |> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")");
        flux.append(" |> sort(columns: [\"_time\"])");

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux.toString());

        List<TrafficStatisticsVO> result = new ArrayList<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                TrafficStatisticsVO vo = new TrafficStatisticsVO();
                Instant instant = record.getTime();
                if (instant != null) {
                    vo.setStatTime(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
                }
                Map<String, Object> values = record.getValues();
                vo.setCameraId(getLongValue(values.get("camera_id")));
                vo.setCameraName(getStringValue(values.get("camera_name")));
                vo.setRoadName(getStringValue(values.get("road_name")));
                vo.setLaneNo(getIntegerValue(values.get("lane_no")));
                vo.setTargetClass(getStringValue(values.get("target_class")));
                vo.setFlowVolume(getIntegerValue(values.get("flow_volume")));
                vo.setAvgSpeed(getBigDecimalValue(values.get("avg_speed")));
                vo.setOccupancy(getBigDecimalValue(values.get("occupancy")));
                vo.setDensity(getBigDecimalValue(values.get("density")));
                vo.setAggregateType(query.getAggregateType());
                result.add(vo);
            }
        }
        return result;
    }

    public List<TrafficRealtimeVO> getRealtimeData(Long cameraId) {
        List<TrafficRealtimeVO> result = new ArrayList<>();
        Camera camera = cameraMapper.selectById(cameraId);
        if (camera == null) return result;

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(5);

        TrafficStatisticsQuery query = new TrafficStatisticsQuery();
        query.setCameraId(cameraId);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setAggregateType("minute");

        List<TrafficStatisticsVO> stats = queryStatistics(query);

        if (CollectionUtils.isEmpty(stats)) {
            int laneCount = camera.getLaneCount() != null ? camera.getLaneCount() : 1;
            for (int i = 1; i <= Math.max(laneCount, 1); i++) {
                result.add(generateNoDataRealtime(camera, i));
            }
            return result;
        }

        Map<Integer, List<TrafficStatisticsVO>> laneMap = stats.stream()
                .collect(Collectors.groupingBy(s -> s.getLaneNo() == null ? 0 : s.getLaneNo()));

        for (Map.Entry<Integer, List<TrafficStatisticsVO>> entry : laneMap.entrySet()) {
            Integer laneNo = entry.getKey();
            List<TrafficStatisticsVO> laneStats = entry.getValue();
            if (CollectionUtils.isEmpty(laneStats)) continue;

            TrafficStatisticsVO latest = laneStats.get(laneStats.size() - 1);
            TrafficRealtimeVO vo = new TrafficRealtimeVO();
            vo.setCameraId(cameraId);
            vo.setCameraName(camera.getCameraName());
            vo.setRoadName(camera.getRoadName());
            vo.setLaneNo(laneNo);
            vo.setLaneName(laneNo > 0 ? (laneNo + "号车道") : "全部车道");
            vo.setTimestamp(latest.getStatTime());
            vo.setFlowVolume(latest.getFlowVolume());
            vo.setAvgSpeed(latest.getAvgSpeed());
            vo.setOccupancy(latest.getOccupancy());
            vo.setDensity(latest.getDensity());

            String level = evaluateTrafficLevel(vo);
            vo.setLevel(level);
            vo.setLevelName(getLevelName(level));

            result.add(vo);
        }

        if (CollectionUtils.isEmpty(result)) {
            int laneCount = camera.getLaneCount() != null ? camera.getLaneCount() : 1;
            for (int i = 1; i <= Math.max(laneCount, 1); i++) {
                result.add(generateNoDataRealtime(camera, i));
            }
        }

        return result;
    }

    public Map<String, Object> getTrafficOverview() {
        Map<String, Object> result = new HashMap<>();
        List<Camera> cameras = cameraMapper.selectList(
                new LambdaQueryWrapper<Camera>().eq(Camera::getStatus, 1));

        int totalFlow = 0;
        BigDecimal totalAvgSpeed = BigDecimal.ZERO;
        int speedCount = 0;
        int congestedLanes = 0;
        int smoothLanes = 0;
        int slowLanes = 0;

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(15);

        List<TrafficRealtimeVO> allRealtime = new ArrayList<>();
        for (Camera cam : cameras) {
            List<TrafficRealtimeVO> realtime = getRealtimeData(cam.getId());
            allRealtime.addAll(realtime);
        }

        for (TrafficRealtimeVO vo : allRealtime) {
            if ("NO_DATA".equals(vo.getLevel())) continue;
            if (vo.getFlowVolume() != null) {
                totalFlow += vo.getFlowVolume();
            }
            if (vo.getAvgSpeed() != null && vo.getAvgSpeed().compareTo(BigDecimal.ZERO) > 0) {
                totalAvgSpeed = totalAvgSpeed.add(vo.getAvgSpeed());
                speedCount++;
            }
            String level = vo.getLevel() == null ? evaluateTrafficLevel(vo) : vo.getLevel();
            switch (level) {
                case "CONGESTED": congestedLanes++; break;
                case "SLOW": slowLanes++; break;
                case "SMOOTH": smoothLanes++; break;
            }
        }

        result.put("totalFlow", totalFlow);
        result.put("avgSpeed", speedCount > 0
                ? totalAvgSpeed.divide(BigDecimal.valueOf(speedCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        result.put("congestedLanes", congestedLanes);
        result.put("slowLanes", slowLanes);
        result.put("smoothLanes", smoothLanes);
        result.put("totalLanes", congestedLanes + slowLanes + smoothLanes);
        result.put("activeCameras", cameras.size());
        result.put("realtimeList", allRealtime);

        return result;
    }

    public void aggregateStatistics(TrafficStatisticsQuery query) {
        LocalDateTime startTime = query.getStartTime();
        LocalDateTime endTime = query.getEndTime();
        if (startTime == null) startTime = LocalDateTime.now().minusMinutes(5);
        if (endTime == null) endTime = LocalDateTime.now();

        LambdaQueryWrapper<Camera> cameraWrapper = new LambdaQueryWrapper<>();
        cameraWrapper.eq(Camera::getStatus, 1);
        if (query.getCameraId() != null) {
            cameraWrapper.eq(Camera::getId, query.getCameraId());
        }
        List<Camera> cameras = cameraMapper.selectList(cameraWrapper);

        for (Camera camera : cameras) {
            aggregateCameraStatistics(camera, startTime, endTime, query.getAggregateType());
        }
    }

    public void processTrackPoint(TrackPoint trackPoint, GlobalTrack globalTrack) {
        if (trackPoint == null || trackPoint.getCameraId() == null) return;

        Long cameraId = trackPoint.getCameraId();
        Camera camera = cameraMapper.selectById(cameraId);
        int laneCount = camera != null && camera.getLaneCount() != null ? camera.getLaneCount() : 1;
        Integer laneNo = inferLaneNumber(trackPoint, laneCount);

        String laneKey = cameraId + "_" + laneNo;
        String speedKey = cameraId + "_" + laneNo + "_speed";
        String occKey = cameraId + "_" + laneNo + "_occ";

        cameraLaneVehicles.computeIfAbsent(cameraId, k -> ConcurrentHashMap.newKeySet());
        if (trackPoint.getTrackId() != null) {
            cameraLaneVehicles.get(cameraId).add(trackPoint.getTrackId());
        }

        if (trackPoint.getSpeed() != null) {
            cameraLaneSpeeds.computeIfAbsent(speedKey, k -> Collections.synchronizedList(new ArrayList<>()));
            cameraLaneSpeeds.get(speedKey).add(trackPoint.getSpeed());
        }

        cameraLaneOccupancyPoints.merge(occKey, 1, Integer::sum);
    }

    @Scheduled(fixedRate = 60000)
    public void scheduledMinuteAggregation() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime minuteStart = now.truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime minuteEnd = minuteStart.plusMinutes(1);

        try {
            LambdaQueryWrapper<Camera> cameraWrapper = new LambdaQueryWrapper<Camera>()
                    .eq(Camera::getStatus, 1)
                    .eq(Camera::getOnlineStatus, 1);
            List<Camera> cameras = cameraMapper.selectList(cameraWrapper);

            for (Camera camera : cameras) {
                aggregateCameraStatistics(camera, minuteStart, minuteEnd, "minute");
            }

            cameraLaneVehicles.clear();
            cameraLaneSpeeds.clear();
            cameraLaneOccupancyPoints.clear();

            log.info("Minute traffic aggregation completed at {}", now);
        } catch (Exception e) {
            log.error("Scheduled minute aggregation failed", e);
        }
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledHourAggregation() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime hourStart = now.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime hourEnd = hourStart.plusHours(1);

        try {
            LambdaQueryWrapper<Camera> cameraWrapper = new LambdaQueryWrapper<Camera>()
                    .eq(Camera::getStatus, 1);
            List<Camera> cameras = cameraMapper.selectList(cameraWrapper);

            for (Camera camera : cameras) {
                aggregateCameraStatistics(camera, hourStart, hourEnd, "hour");
            }

            log.info("Hour traffic aggregation completed at {}", now);
        } catch (Exception e) {
            log.error("Scheduled hour aggregation failed", e);
        }
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void scheduledDayAggregation() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        try {
            LambdaQueryWrapper<Camera> cameraWrapper = new LambdaQueryWrapper<Camera>()
                    .eq(Camera::getStatus, 1);
            List<Camera> cameras = cameraMapper.selectList(cameraWrapper);

            for (Camera camera : cameras) {
                aggregateCameraStatistics(camera, dayStart, dayEnd, "day");
            }

            log.info("Day traffic aggregation completed at {}", now);
        } catch (Exception e) {
            log.error("Scheduled day aggregation failed", e);
        }
    }

    private void aggregateCameraStatistics(Camera camera, LocalDateTime startTime,
                                           LocalDateTime endTime, String aggregateType) {
        int laneCount = camera.getLaneCount() != null ? camera.getLaneCount() : 1;

        for (int laneNo = 1; laneNo <= Math.max(laneCount, 1); laneNo++) {
            TrafficStatistics stats = calculateLaneStatistics(
                    camera, laneNo, startTime, endTime, aggregateType);

            saveToMySQL(stats);

            saveToInfluxDB(stats);
        }
    }

    private TrafficStatistics calculateLaneStatistics(Camera camera, int laneNo,
                                                      LocalDateTime startTime, LocalDateTime endTime,
                                                      String aggregateType) {
        TrafficStatistics stats = new TrafficStatistics();
        stats.setCameraId(camera.getId());
        stats.setCameraName(camera.getCameraName());
        stats.setRoadName(camera.getRoadName());
        stats.setLaneNo(laneNo);
        stats.setLaneName(laneNo + "号车道");
        stats.setStatTime(startTime);
        stats.setStartTime(startTime);
        stats.setEndTime(endTime);
        stats.setAggregateType(aggregateType);

        LambdaQueryWrapper<TrackPoint> tpWrapper = new LambdaQueryWrapper<>();
        tpWrapper.eq(TrackPoint::getCameraId, camera.getId());
        tpWrapper.ge(TrackPoint::getFrameTime, startTime);
        tpWrapper.lt(TrackPoint::getFrameTime, endTime);

        if (camera.getLaneCount() != null && camera.getLaneCount() > 1) {
            List<Integer> x1Range = getLaneX1Range(camera, laneNo);
            if (x1Range != null) {
                tpWrapper.ge(TrackPoint::getBboxX1, x1Range.get(0));
                tpWrapper.lt(TrackPoint::getBboxX1, x1Range.get(1));
            }
        }

        List<TrackPoint> trackPoints = trackPointMapper.selectList(tpWrapper);

        Set<Long> uniqueTrackIds = new HashSet<>();
        List<BigDecimal> speeds = new ArrayList<>();
        int occupiedFrames = 0;
        int totalFrames = (int) Duration.between(startTime, endTime).getSeconds() * 2;
        if (totalFrames <= 0) totalFrames = 60;

        for (TrackPoint tp : trackPoints) {
            if (tp.getTrackId() != null) {
                uniqueTrackIds.add(tp.getTrackId());
            }
            if (tp.getSpeed() != null && tp.getSpeed().compareTo(BigDecimal.ZERO) >= 0) {
                speeds.add(tp.getSpeed());
            }
            occupiedFrames++;
        }

        stats.setFlowVolume(uniqueTrackIds.size());
        stats.setVehicleCount(uniqueTrackIds.size());

        if (!speeds.isEmpty()) {
            BigDecimal sum = speeds.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            stats.setAvgSpeed(sum.divide(BigDecimal.valueOf(speeds.size()), 2, RoundingMode.HALF_UP));
            stats.setMinSpeed(speeds.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
            stats.setMaxSpeed(speeds.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));

            BigDecimal mean = stats.getAvgSpeed();
            BigDecimal variance = speeds.stream()
                    .map(s -> s.subtract(mean).pow(2))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(speeds.size()), 4, RoundingMode.HALF_UP);
            stats.setSpeedStandardDeviation(new BigDecimal(Math.sqrt(variance.doubleValue()))
                    .setScale(2, RoundingMode.HALF_UP));
        } else {
            stats.setAvgSpeed(BigDecimal.ZERO);
            stats.setMinSpeed(BigDecimal.ZERO);
            stats.setMaxSpeed(BigDecimal.ZERO);
            stats.setSpeedStandardDeviation(BigDecimal.ZERO);
        }

        BigDecimal occupancy = BigDecimal.valueOf(occupiedFrames)
                .divide(BigDecimal.valueOf(Math.max(totalFrames, 1)), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        stats.setOccupancy(occupancy.min(BigDecimal.valueOf(100)));

        stats.setDensity(calculateDensity(stats.getFlowVolume(), stats.getAvgSpeed(), aggregateType));

        if (stats.getFlowVolume() > 0) {
            long durationSec = Duration.between(startTime, endTime).getSeconds();
            if (durationSec > 0) {
                BigDecimal headway = BigDecimal.valueOf(durationSec)
                        .divide(BigDecimal.valueOf(stats.getFlowVolume()), 2, RoundingMode.HALF_UP);
                stats.setAvgHeadway(headway);
            }
        }

        return stats;
    }

    private BigDecimal calculateDensity(Integer flowVolume, BigDecimal avgSpeed, String aggregateType) {
        if (flowVolume == null || flowVolume == 0) return BigDecimal.ZERO;
        if (avgSpeed == null || avgSpeed.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        BigDecimal flowPerHour;
        switch (aggregateType) {
            case "minute":
                flowPerHour = BigDecimal.valueOf(flowVolume).multiply(BigDecimal.valueOf(60));
                break;
            case "hour":
                flowPerHour = BigDecimal.valueOf(flowVolume);
                break;
            case "day":
                flowPerHour = BigDecimal.valueOf(flowVolume).divide(BigDecimal.valueOf(24), 2, RoundingMode.HALF_UP);
                break;
            default:
                flowPerHour = BigDecimal.valueOf(flowVolume);
        }

        return flowPerHour.divide(avgSpeed, 2, RoundingMode.HALF_UP);
    }

    private List<Integer> getLaneX1Range(Camera camera, int laneNo) {
        if (camera.getResolutionWidth() != null && camera.getResolutionWidth() > 0
                && camera.getLaneCount() != null && camera.getLaneCount() > 0) {
            int laneWidth = camera.getResolutionWidth() / camera.getLaneCount();
            return Arrays.asList((laneNo - 1) * laneWidth, laneNo * laneWidth);
        }
        int defaultWidth = 1920;
        int laneCount = camera.getLaneCount() != null ? camera.getLaneCount() : 1;
        int laneWidth = defaultWidth / Math.max(laneCount, 1);
        return Arrays.asList((laneNo - 1) * laneWidth, laneNo * laneWidth);
    }

    private Integer inferLaneNumber(TrackPoint trackPoint, int laneCount) {
        if (laneCount <= 1) return 1;
        if (trackPoint.getPixelX() == null) return 1;
        BigDecimal pixelX = trackPoint.getPixelX();
        BigDecimal laneWidth = BigDecimal.ONE.divide(BigDecimal.valueOf(laneCount), 4, RoundingMode.HALF_UP);
        int lane = pixelX.divide(laneWidth, 0, RoundingMode.FLOOR).intValue() + 1;
        return Math.min(Math.max(lane, 1), laneCount);
    }

    private void saveToMySQL(TrafficStatistics stats) {
        try {
            LambdaQueryWrapper<TrafficStatistics> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TrafficStatistics::getCameraId, stats.getCameraId());
            wrapper.eq(TrafficStatistics::getLaneNo, stats.getLaneNo());
            wrapper.eq(TrafficStatistics::getStatTime, stats.getStatTime());
            wrapper.eq(TrafficStatistics::getAggregateType, stats.getAggregateType());

            TrafficStatistics existing = trafficStatisticsMapper.selectOne(wrapper);
            if (existing != null) {
                stats.setId(existing.getId());
                trafficStatisticsMapper.updateById(stats);
            } else {
                trafficStatisticsMapper.insert(stats);
            }
        } catch (Exception e) {
            log.error("Save traffic statistics to MySQL failed: {}", e.getMessage(), e);
        }
    }

    private void saveToInfluxDB(TrafficStatistics stats) {
        if (writeApiBlocking == null || stats.getStatTime() == null) return;

        try {
            Point point = Point.measurement(MEASUREMENT)
                    .addTag("camera_id", String.valueOf(stats.getCameraId()))
                    .addTag("camera_name", stats.getCameraName() != null ? stats.getCameraName() : "")
                    .addTag("road_name", stats.getRoadName() != null ? stats.getRoadName() : "")
                    .addTag("lane_no", String.valueOf(stats.getLaneNo()))
                    .addTag("lane_name", stats.getLaneName() != null ? stats.getLaneName() : "")
                    .addTag("aggregate_type", stats.getAggregateType() != null ? stats.getAggregateType() : "minute")
                    .addField("flow_volume", stats.getFlowVolume() != null ? stats.getFlowVolume() : 0)
                    .addField("vehicle_count", stats.getVehicleCount() != null ? stats.getVehicleCount() : 0)
                    .addField("avg_speed", stats.getAvgSpeed() != null ? stats.getAvgSpeed().doubleValue() : 0.0)
                    .addField("min_speed", stats.getMinSpeed() != null ? stats.getMinSpeed().doubleValue() : 0.0)
                    .addField("max_speed", stats.getMaxSpeed() != null ? stats.getMaxSpeed().doubleValue() : 0.0)
                    .addField("speed_std_dev", stats.getSpeedStandardDeviation() != null
                            ? stats.getSpeedStandardDeviation().doubleValue() : 0.0)
                    .addField("occupancy", stats.getOccupancy() != null ? stats.getOccupancy().doubleValue() : 0.0)
                    .addField("density", stats.getDensity() != null ? stats.getDensity().doubleValue() : 0.0)
                    .addField("avg_headway", stats.getAvgHeadway() != null
                            ? stats.getAvgHeadway().doubleValue() : 0.0);

            if (stats.getStatTime() != null) {
                Instant instant = stats.getStatTime().atZone(ZoneId.systemDefault()).toInstant();
                point.time(instant, WritePrecision.MS);
            }

            writeApiBlocking.writePoint(point);
        } catch (Exception e) {
            log.warn("Save traffic statistics to InfluxDB failed: {}", e.getMessage());
        }
    }

    private TrafficStatisticsVO convertToVO(TrafficStatistics entity) {
        TrafficStatisticsVO vo = new TrafficStatisticsVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private TrafficRealtimeVO generateNoDataRealtime(Camera camera, int laneNo) {
        TrafficRealtimeVO vo = new TrafficRealtimeVO();
        vo.setCameraId(camera.getId());
        vo.setCameraName(camera.getCameraName());
        vo.setRoadName(camera.getRoadName());
        vo.setLaneNo(laneNo);
        vo.setLaneName(laneNo + "号车道");
        vo.setTimestamp(null);
        vo.setFlowVolume(null);
        vo.setAvgSpeed(null);
        vo.setOccupancy(null);
        vo.setDensity(null);
        vo.setLevel("NO_DATA");
        vo.setLevelName("无数据");
        return vo;
    }

    private String evaluateTrafficLevel(TrafficRealtimeVO vo) {
        BigDecimal speed = vo.getAvgSpeed() != null ? vo.getAvgSpeed() : BigDecimal.ZERO;
        BigDecimal occupancy = vo.getOccupancy() != null ? vo.getOccupancy() : BigDecimal.ZERO;

        if (speed.compareTo(BigDecimal.valueOf(30)) < 0
                || occupancy.compareTo(BigDecimal.valueOf(60)) > 0) {
            return "CONGESTED";
        } else if (speed.compareTo(BigDecimal.valueOf(50)) < 0
                || occupancy.compareTo(BigDecimal.valueOf(40)) > 0) {
            return "SLOW";
        }
        return "SMOOTH";
    }

    private String getLevelName(String level) {
        switch (level) {
            case "CONGESTED": return "拥堵";
            case "SLOW": return "缓行";
            case "NO_DATA": return "无数据";
            default: return "畅通";
        }
    }

    private String toRFC3339(LocalDateTime dateTime) {
        return dateTime.atOffset(ZoneOffset.ofHours(8)).toString();
    }

    private Long getLongValue(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return null; }
    }

    private Integer getIntegerValue(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return null; }
    }

    private BigDecimal getBigDecimalValue(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
        try { return new BigDecimal(val.toString()); } catch (Exception e) { return null; }
    }

    private String getStringValue(Object val) {
        return val == null ? null : val.toString();
    }

    public void addDatabaseTable() {
        String sql = "CREATE TABLE IF NOT EXISTS traffic_statistics (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "camera_id BIGINT," +
                "camera_name VARCHAR(128)," +
                "road_name VARCHAR(128)," +
                "lane_no INT," +
                "lane_name VARCHAR(32)," +
                "target_class VARCHAR(32)," +
                "stat_time DATETIME NOT NULL," +
                "start_time DATETIME," +
                "end_time DATETIME," +
                "flow_volume INT DEFAULT 0," +
                "avg_speed DECIMAL(8,2) DEFAULT 0," +
                "min_speed DECIMAL(8,2) DEFAULT 0," +
                "max_speed DECIMAL(8,2) DEFAULT 0," +
                "speed_standard_deviation DECIMAL(8,2) DEFAULT 0," +
                "occupancy DECIMAL(8,4) DEFAULT 0," +
                "density DECIMAL(8,2) DEFAULT 0," +
                "avg_headway DECIMAL(8,2) DEFAULT 0," +
                "vehicle_count INT DEFAULT 0," +
                "aggregate_type VARCHAR(16) DEFAULT 'minute'," +
                "create_time DATETIME," +
                "update_time DATETIME," +
                "deleted INT DEFAULT 0," +
                "INDEX idx_camera_id (camera_id)," +
                "INDEX idx_stat_time (stat_time)," +
                "INDEX idx_lane_no (lane_no)," +
                "INDEX idx_aggregate_type (aggregate_type)," +
                "UNIQUE KEY uk_cam_lane_time (camera_id, lane_no, stat_time, aggregate_type)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        log.info("Please execute the following SQL manually: \n{}", sql);
    }
}

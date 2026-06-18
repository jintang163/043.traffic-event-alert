package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.GlobalTrackQuery;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.entity.EventTrackLink;
import com.traffic.alert.entity.GlobalTrack;
import com.traffic.alert.entity.TrackMatchLog;
import com.traffic.alert.entity.TrackPoint;
import com.traffic.alert.mapper.EventTrackLinkMapper;
import com.traffic.alert.mapper.GlobalTrackMapper;
import com.traffic.alert.mapper.TrackMatchLogMapper;
import com.traffic.alert.mapper.TrackPointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalTrackService {

    private final GlobalTrackMapper globalTrackMapper;
    private final TrackPointMapper trackPointMapper;
    private final TrackMatchLogMapper trackMatchLogMapper;
    private final EventTrackLinkMapper eventTrackLinkMapper;
    private final CameraService cameraService;

    private static final DateTimeFormatter TRACK_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final double PLATE_MATCH_THRESHOLD = 0.90;
    private static final double REID_MATCH_THRESHOLD = 0.75;
    private static final double JOINT_MATCH_THRESHOLD = 0.82;

    public GlobalTrack getById(Long id) {
        return globalTrackMapper.selectById(id);
    }

    public PageResult<GlobalTrack> page(GlobalTrackQuery query) {
        Page<GlobalTrack> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<GlobalTrack> wrapper = new LambdaQueryWrapper<>();

        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(GlobalTrack::getTrackNo, query.getKeyword())
                    .or().like(GlobalTrack::getLicensePlate, query.getKeyword());
        }
        if (query.getTrackNo() != null && !query.getTrackNo().isEmpty()) {
            wrapper.like(GlobalTrack::getTrackNo, query.getTrackNo());
        }
        if (query.getTargetClass() != null && !query.getTargetClass().isEmpty()) {
            wrapper.eq(GlobalTrack::getTargetClass, query.getTargetClass());
        }
        if (query.getLicensePlate() != null && !query.getLicensePlate().isEmpty()) {
            wrapper.like(GlobalTrack::getLicensePlate, query.getLicensePlate());
        }
        if (query.getCameraId() != null) {
            wrapper.and(w -> w.eq(GlobalTrack::getFirstCameraId, query.getCameraId())
                    .or().eq(GlobalTrack::getLastCameraId, query.getCameraId()));
        }
        if (query.getTrackStatus() != null) {
            wrapper.eq(GlobalTrack::getTrackStatus, query.getTrackStatus());
        }
        if (query.getIsEventTarget() != null) {
            wrapper.eq(GlobalTrack::getIsEventTarget, query.getIsEventTarget());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(GlobalTrack::getFirstSeenTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(GlobalTrack::getLastSeenTime, query.getEndTime());
        }

        wrapper.orderByDesc(GlobalTrack::getLastSeenTime);
        globalTrackMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
    }

    public List<TrackPoint> listTrackPoints(Long trackId) {
        LambdaQueryWrapper<TrackPoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrackPoint::getTrackId, trackId)
                .orderByAsc(TrackPoint::getFrameTime);
        return trackPointMapper.selectList(wrapper);
    }

    public List<TrackPoint> listKeyPoints(Long trackId) {
        LambdaQueryWrapper<TrackPoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrackPoint::getTrackId, trackId)
                .eq(TrackPoint::getIsKeyPoint, 1)
                .orderByAsc(TrackPoint::getFrameTime);
        return trackPointMapper.selectList(wrapper);
    }

    public List<GlobalTrack> listActiveTracks() {
        LambdaQueryWrapper<GlobalTrack> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GlobalTrack::getTrackStatus, 1)
                .orderByDesc(GlobalTrack::getLastSeenTime);
        return globalTrackMapper.selectList(wrapper);
    }

    public List<GlobalTrack> listByEvent(Long eventId) {
        return Collections.emptyList();
    }

    @Transactional
    public GlobalTrack createTrack(GlobalTrack track) {
        if (track.getTrackNo() == null || track.getTrackNo().isEmpty()) {
            String trackNo = "TRK" + LocalDateTime.now().format(TRACK_NO_FORMATTER)
                    + String.format("%04d", (int) (Math.random() * 10000));
            track.setTrackNo(trackNo);
        }
        if (track.getCameraCount() == null) track.setCameraCount(1);
        if (track.getPointCount() == null) track.setPointCount(0);
        if (track.getTrackStatus() == null) track.setTrackStatus(1);
        if (track.getIsEventTarget() == null) track.setIsEventTarget(0);
        if (track.getLinkedEventCount() == null) track.setLinkedEventCount(0);

        globalTrackMapper.insert(track);
        log.info("创建全局轨迹: trackNo={}, targetClass={}, plate={}",
                track.getTrackNo(), track.getTargetClass(), track.getLicensePlate());
        return track;
    }

    @Transactional
    public GlobalTrack updateTrack(GlobalTrack track) {
        GlobalTrack exist = getById(track.getId());
        if (exist == null) {
            throw new BusinessException("轨迹不存在");
        }
        globalTrackMapper.updateById(track);
        return track;
    }

    @Transactional
    public TrackPoint addTrackPoint(TrackPoint point) {
        trackPointMapper.insert(point);

        GlobalTrack track = getById(point.getTrackId());
        if (track != null) {
            track.setPointCount((track.getPointCount() == null ? 0 : track.getPointCount()) + 1);
            track.setLastSeenTime(point.getFrameTime());
            if (point.getLongitude() != null) track.setLastLongitude(point.getLongitude());
            if (point.getLatitude() != null) track.setLastLatitude(point.getLatitude());
            track.setLastCameraId(point.getCameraId());
            track.setLastCameraName(point.getCameraName());
            globalTrackMapper.updateById(track);
        }
        return point;
    }

    @Transactional
    public TrackMatchLog matchAcrossCameras(
            String sourcePlate, String sourceReidFeature, String sourceTargetClass,
            Long sourceCameraId, LocalDateTime sourceLeaveTime,
            Long targetCameraId, String targetPlate, String targetReidFeature,
            Long targetTrackId, String targetTrackNo
    ) {
        TrackMatchLog log = new TrackMatchLog();
        log.setMatchTime(LocalDateTime.now());
        log.setSourceCameraId(sourceCameraId);
        log.setTargetCameraId(targetCameraId);
        log.setTargetCamTrackId(targetTrackId);
        log.setTargetTrackNo(targetTrackNo);

        double plateScore = 0.0;
        if (sourcePlate != null && targetPlate != null && !sourcePlate.isEmpty() && !targetPlate.isEmpty()) {
            plateScore = sourcePlate.equalsIgnoreCase(targetPlate) ? 1.0 : 0.0;
        }

        double reidScore = 0.0;
        if (sourceReidFeature != null && targetReidFeature != null
                && !sourceReidFeature.isEmpty() && !targetReidFeature.isEmpty()) {
            reidScore = cosineSimilarity(parseFeature(sourceReidFeature), parseFeature(targetReidFeature));
        }

        double jointScore;
        int matchMethod;
        if (plateScore >= PLATE_MATCH_THRESHOLD) {
            jointScore = plateScore;
            matchMethod = 1;
        } else if (reidScore >= REID_MATCH_THRESHOLD) {
            jointScore = reidScore;
            matchMethod = 2;
        } else {
            jointScore = (plateScore * 0.6 + reidScore * 0.4);
            matchMethod = 3;
        }

        log.setPlateMatchScore(BigDecimal.valueOf(plateScore).setScale(4, RoundingMode.HALF_UP));
        log.setReidMatchScore(BigDecimal.valueOf(reidScore).setScale(4, RoundingMode.HALF_UP));
        log.setMatchScore(BigDecimal.valueOf(jointScore).setScale(4, RoundingMode.HALF_UP));
        log.setMatchMethod(matchMethod);

        Camera sourceCam = cameraService.getById(sourceCameraId);
        Camera targetCam = cameraService.getById(targetCameraId);
        if (sourceCam != null && targetCam != null
                && sourceCam.getLongitude() != null && targetCam.getLongitude() != null) {
            double distMeters = haversineDistance(
                    sourceCam.getLongitude().doubleValue(), sourceCam.getLatitude().doubleValue(),
                    targetCam.getLongitude().doubleValue(), targetCam.getLatitude().doubleValue()
            );
            int expected = (int) (distMeters / 20.0);
            log.setExpectedSeconds(Math.max(expected, 5));
            if (sourceLeaveTime != null) {
                log.setTravelSeconds((int) (java.time.Duration.between(sourceLeaveTime, LocalDateTime.now()).getSeconds()));
            }
        }

        boolean isSuccess = jointScore >= JOINT_MATCH_THRESHOLD;
        log.setIsSuccess(isSuccess ? 1 : 0);
        if (!isSuccess) {
            log.setReason(String.format("联合得分%.3f低于阈值%.3f (plate=%.3f, reid=%.3f)",
                    jointScore, JOINT_MATCH_THRESHOLD, plateScore, reidScore));
        }
        trackMatchLogMapper.insert(log);

        if (isSuccess) {
            log.info("跨摄像头匹配成功: 摄像头{}→{}, plateScore={}, reidScore={}, joint={}",
                    sourceCameraId, targetCameraId, plateScore, reidScore, jointScore);
        } else {
            log.debug("跨摄像头匹配失败: 摄像头{}→{}, plateScore={}, reidScore={}, joint={}",
                    sourceCameraId, targetCameraId, plateScore, reidScore, jointScore);
        }
        return log;
    }

    public GlobalTrack findMatchingTrack(String licensePlate, String reidFeature, String targetClass,
                                         Long cameraId, double threshold) {
        LambdaQueryWrapper<GlobalTrack> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GlobalTrack::getTrackStatus, 1);
        if (targetClass != null && !targetClass.isEmpty()) {
            wrapper.eq(GlobalTrack::getTargetClass, targetClass);
        }
        wrapper.ne(GlobalTrack::getLastCameraId, cameraId);
        wrapper.last("ORDER BY last_seen_time DESC LIMIT 50");

        List<GlobalTrack> candidates = globalTrackMapper.selectList(wrapper);
        GlobalTrack bestMatch = null;
        double bestScore = 0;

        for (GlobalTrack t : candidates) {
            double score = 0;
            if (licensePlate != null && t.getLicensePlate() != null
                    && !licensePlate.isEmpty() && !t.getLicensePlate().isEmpty()) {
                score = licensePlate.equalsIgnoreCase(t.getLicensePlate()) ? 1.0 : 0;
            }
            if (score < 0.5 && reidFeature != null && t.getReidFeature() != null
                    && !reidFeature.isEmpty() && !t.getReidFeature().isEmpty()) {
                double reidScore = cosineSimilarity(parseFeature(reidFeature), parseFeature(t.getReidFeature()));
                score = Math.max(score, reidScore);
            }
            if (score > bestScore) {
                bestScore = score;
                bestMatch = t;
            }
        }

        return bestScore >= threshold ? bestMatch : null;
    }

    private List<Double> parseFeature(String featureStr) {
        try {
            featureStr = featureStr.trim();
            if (featureStr.startsWith("[") && featureStr.endsWith("]")) {
                featureStr = featureStr.substring(1, featureStr.length() - 1);
            }
            String[] parts = featureStr.split(",");
            List<Double> feat = new ArrayList<>();
            for (String p : parts) {
                feat.add(Double.parseDouble(p.trim()));
            }
            return feat;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1.isEmpty() || v2.isEmpty() || v1.size() != v2.size()) return 0.0;
        double dot = 0, norm1 = 0, norm2 = 0;
        for (int i = 0; i < v1.size(); i++) {
            dot += v1.get(i) * v2.get(i);
            norm1 += v1.get(i) * v1.get(i);
            norm2 += v2.get(i) * v2.get(i);
        }
        return (norm1 == 0 || norm2 == 0) ? 0.0 : dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private double haversineDistance(double lng1, double lat1, double lng2, double lat2) {
        double R = 6378137.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public Map<String, Object> getTrackTimeline(Long trackId) {
        List<TrackPoint> points = listTrackPoints(trackId);
        GlobalTrack track = getById(trackId);

        Map<Long, List<TrackPoint>> cameraGroups = new LinkedHashMap<>();
        List<Map<String, Object>> segments = new ArrayList<>();
        Long currentCameraId = null;
        List<TrackPoint> currentSegment = null;

        for (TrackPoint p : points) {
            if (!Objects.equals(p.getCameraId(), currentCameraId)) {
                if (currentCameraId != null && currentSegment != null && !currentSegment.isEmpty()) {
                    Camera cam = cameraService.getById(currentCameraId);
                    segments.add(Map.of(
                            "cameraId", currentCameraId,
                            "cameraName", cam != null ? cam.getCameraName() : "",
                            "startTime", currentSegment.get(0).getFrameTime(),
                            "endTime", currentSegment.get(currentSegment.size() - 1).getFrameTime(),
                            "pointCount", currentSegment.size(),
                            "points", currentSegment
                    ));
                }
                currentCameraId = p.getCameraId();
                currentSegment = new ArrayList<>();
            }
            if (currentSegment != null) currentSegment.add(p);
            cameraGroups.computeIfAbsent(p.getCameraId(), k -> new ArrayList<>()).add(p);
        }
        if (currentCameraId != null && currentSegment != null && !currentSegment.isEmpty()) {
            Camera cam = cameraService.getById(currentCameraId);
            segments.add(Map.of(
                    "cameraId", currentCameraId,
                    "cameraName", cam != null ? cam.getCameraName() : "",
                    "startTime", currentSegment.get(0).getFrameTime(),
                    "endTime", currentSegment.get(currentSegment.size() - 1).getFrameTime(),
                    "pointCount", currentSegment.size(),
                    "points", currentSegment
            ));
        }

        return Map.of(
                "track", track != null ? track : Collections.emptyMap(),
                "segments", segments,
                "cameraGroups", cameraGroups,
                "totalPoints", points.size(),
                "cameraCount", segments.size()
        );
    }

    public void updateTrackStatus(Long trackId, int status) {
        GlobalTrack t = getById(trackId);
        if (t != null) {
            t.setTrackStatus(status);
            globalTrackMapper.updateById(t);
        }
    }

    @Transactional
    public int batchAddTrackPoints(List<TrackPoint> points) {
        if (points == null || points.isEmpty()) return 0;
        for (TrackPoint p : points) {
            trackPointMapper.insert(p);
        }

        Set<Long> trackIds = new HashSet<>();
        for (TrackPoint p : points) {
            if (p.getTrackId() != null) {
                trackIds.add(p.getTrackId());
            }
        }

        for (Long trackId : trackIds) {
            GlobalTrack track = getById(trackId);
            if (track != null) {
                long count = points.stream().filter(p -> trackId.equals(p.getTrackId())).count();
                track.setPointCount((track.getPointCount() == null ? 0 : track.getPointCount()) + (int) count);

                TrackPoint last = points.stream()
                        .filter(p -> trackId.equals(p.getTrackId()))
                        .max(Comparator.comparing(TrackPoint::getFrameTime))
                        .orElse(null);
                if (last != null) {
                    track.setLastSeenTime(last.getFrameTime());
                    if (last.getLongitude() != null) track.setLastLongitude(last.getLongitude());
                    if (last.getLatitude() != null) track.setLastLatitude(last.getLatitude());
                    track.setLastCameraId(last.getCameraId());
                    track.setLastCameraName(last.getCameraName());
                }

                TrackPoint first = points.stream()
                        .filter(p -> trackId.equals(p.getTrackId()))
                        .min(Comparator.comparing(TrackPoint::getFrameTime))
                        .orElse(null);
                if (first != null && track.getFirstSeenTime() == null) {
                    track.setFirstSeenTime(first.getFrameTime());
                    track.setFirstCameraId(first.getCameraId());
                    track.setFirstCameraName(first.getCameraName());
                }

                globalTrackMapper.updateById(track);
            }
        }

        return points.size();
    }

    @Transactional
    public EventTrackLink linkEventToTrack(Long eventId, String eventNo, Long trackId, String trackNo,
                                           Integer linkType, BigDecimal linkConfidence,
                                           Long cameraId, Long trackPointId, String description) {
        EventTrackLink link = new EventTrackLink();
        link.setEventId(eventId);
        link.setEventNo(eventNo);
        link.setTrackId(trackId);
        link.setTrackNo(trackNo);
        link.setLinkType(linkType != null ? linkType : 1);
        link.setLinkConfidence(linkConfidence);
        link.setCameraId(cameraId);
        link.setTrackPointId(trackPointId);
        link.setDescription(description);
        eventTrackLinkMapper.insert(link);

        GlobalTrack track = getById(trackId);
        if (track != null) {
            track.setIsEventTarget(1);
            track.setLinkedEventCount((track.getLinkedEventCount() == null ? 0 : track.getLinkedEventCount()) + 1);
            globalTrackMapper.updateById(track);
        }

        log.info("关联告警事件与轨迹: eventId={}, trackId={}, linkType={}", eventId, trackId, linkType);
        return link;
    }

    public List<GlobalTrack> listByEvent(Long eventId) {
        LambdaQueryWrapper<EventTrackLink> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EventTrackLink::getEventId, eventId)
                .orderByAsc(EventTrackLink::getLinkType);
        List<EventTrackLink> links = eventTrackLinkMapper.selectList(wrapper);

        List<GlobalTrack> tracks = new ArrayList<>();
        for (EventTrackLink link : links) {
            GlobalTrack track = getById(link.getTrackId());
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    public List<EventTrackLink> listEventLinks(Long eventId) {
        LambdaQueryWrapper<EventTrackLink> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EventTrackLink::getEventId, eventId);
        return eventTrackLinkMapper.selectList(wrapper);
    }

    public GlobalTrack findOrCreateTrackFromEvent(String licensePlate, String reidFeature,
                                                   String targetClass, Long cameraId,
                                                   String cameraName, LocalDateTime eventTime) {
        GlobalTrack matched = findMatchingTrack(licensePlate, reidFeature, targetClass, cameraId, JOINT_MATCH_THRESHOLD);
        if (matched != null) {
            return matched;
        }

        GlobalTrack track = new GlobalTrack();
        track.setTargetClass(targetClass);
        track.setLicensePlate(licensePlate);
        track.setReidFeature(reidFeature);
        track.setFirstCameraId(cameraId);
        track.setFirstCameraName(cameraName);
        track.setLastCameraId(cameraId);
        track.setLastCameraName(cameraName);
        track.setFirstSeenTime(eventTime);
        track.setLastSeenTime(eventTime);
        track.setCameraCount(1);
        track.setPointCount(0);
        track.setTrackStatus(1);
        track.setIsEventTarget(1);
        track.setLinkedEventCount(0);
        return createTrack(track);
    }
}

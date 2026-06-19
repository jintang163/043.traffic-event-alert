package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.AiEventCallbackRequest;
import com.traffic.alert.dto.AlertEventQuery;
import com.traffic.alert.dto.FalsePositiveRequest;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.entity.Department;
import com.traffic.alert.entity.WorkOrder;
import com.traffic.alert.enums.DebrisCategory;
import com.traffic.alert.mapper.AlertEventMapper;
import com.traffic.alert.websocket.AlertWebSocket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEventService {

    private final AlertEventMapper alertEventMapper;
    private final CameraService cameraService;
    private final DepartmentService departmentService;
    private final WorkOrderService workOrderService;
    private final MinioService minioService;
    private final NotificationService notificationService;
    private final PtzCruiseService ptzCruiseService;
    private final GlobalTrackService globalTrackService;
    private final DebrisClassificationService debrisClassificationService;
    private final AccidentSeverityService accidentSeverityService;
    private final VideoRecordingService videoRecordingService;
    private final PlateRecognitionService plateRecognitionService;
    private final PolicePushService policePushService;

    private static final DateTimeFormatter EVENT_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public AlertEvent getById(Long id) {
        return alertEventMapper.selectById(id);
    }

    public PageResult<AlertEvent> page(AlertEventQuery query) {
        Page<AlertEvent> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(AlertEvent::getEventNo, query.getKeyword())
                    .or().like(AlertEvent::getDescription, query.getKeyword());
        }
        if (query.getEventType() != null && !query.getEventType().isEmpty()) {
            wrapper.eq(AlertEvent::getEventType, query.getEventType());
        }
        if (query.getDebrisCategory() != null && !query.getDebrisCategory().isEmpty()) {
            wrapper.eq(AlertEvent::getDebrisCategory, query.getDebrisCategory());
        }
        if (query.getAccidentSeverity() != null && !query.getAccidentSeverity().isEmpty()) {
            wrapper.eq(AlertEvent::getAccidentSeverity, query.getAccidentSeverity());
        }
        if (query.getEventLevel() != null) {
            wrapper.eq(AlertEvent::getEventLevel, query.getEventLevel());
        }
        if (query.getAlertStatus() != null) {
            wrapper.eq(AlertEvent::getAlertStatus, query.getAlertStatus());
        }
        if (query.getCameraId() != null) {
            wrapper.eq(AlertEvent::getCameraId, query.getCameraId());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(AlertEvent::getEventTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(AlertEvent::getEventTime, query.getEndTime());
        }
        if (query.getIsFalsePositive() != null) {
            wrapper.eq(AlertEvent::getIsFalsePositive, query.getIsFalsePositive());
        }
        wrapper.orderByDesc(AlertEvent::getAccidentPriority)
                .orderByDesc(AlertEvent::getEventTime);
        alertEventMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), query.getSize());
    }

    @Transactional
    public AlertEvent handleAiEventCallback(AiEventCallbackRequest request) {
        Camera camera = cameraService.getById(request.getCameraId());
        if (camera == null) {
            throw new BusinessException("摄像头不存在");
        }

        String eventNo = request.getEventNo() != null && !request.getEventNo().isEmpty()
                ? request.getEventNo()
                : "EVT" + LocalDateTime.now().format(EVENT_NO_FORMATTER) +
                String.format("%04d", (int) (Math.random() * 10000));

        AlertEvent event = new AlertEvent();
        event.setEventNo(eventNo);
        event.setEventType(request.getEventType());

        if ("DEBRIS".equals(request.getEventType())) {
            DebrisCategory category = debrisClassificationService.validateAndResolve(request.getDebrisCategory());
            if (category == null) {
                category = debrisClassificationService.classify(
                        request.getBbox() != null ? (String) request.getBbox().get("className") : null,
                        request.getDescription(),
                        request.getBbox());
            }
            event.setDebrisCategory(category.getCode());
            event.setEventLevel(category.getDefaultLevel());
            log.info("抛洒物分类识别: eventNo={}, category={}({}), level={} (按类别默认等级)",
                    eventNo, category.getCode(), category.getLabel(), category.getDefaultLevel());
        } else {
            event.setEventLevel(request.getEventLevel() != null ? request.getEventLevel() : 1);
        }
        event.setCameraId(camera.getId());
        event.setCameraName(camera.getCameraName());
        event.setLocation(camera.getLocation());
        event.setLongitude(camera.getLongitude());
        event.setLatitude(camera.getLatitude());
        event.setEventTime(request.getEventTime() != null ? request.getEventTime() : LocalDateTime.now());
        event.setConfidence(request.getConfidence() != null ?
                request.getConfidence().setScale(4, RoundingMode.HALF_UP) : BigDecimal.valueOf(0.9));
        event.setDescription(request.getDescription());
        event.setAlertStatus(0);
        event.setIsFalsePositive(0);

        if (request.getEventVideo() != null && !request.getEventVideo().isEmpty()) {
            event.setEventVideo(request.getEventVideo());
        }

        if (request.getSnapshotBase64() != null && !request.getSnapshotBase64().isEmpty()) {
            try {
                byte[] imageBytes = Base64.getDecoder().decode(request.getSnapshotBase64());
                String objectName = "snapshots/" + eventNo + ".jpg";
                String snapshotUrl = minioService.uploadFile(
                        objectName,
                        new ByteArrayInputStream(imageBytes),
                        imageBytes.length,
                        "image/jpeg"
                );
                event.setEventSnapshot(snapshotUrl);
            } catch (Exception e) {
                log.error("保存事件快照失败: {}", e.getMessage());
            }
        }

        if ("ACCIDENT".equals(event.getEventType())) {
            try {
                accidentSeverityService.evaluate(event, request);
                log.info("交通事故严重程度评估: eventNo={}, severity={}({}), priority={}",
                        eventNo, event.getAccidentSeverity(), event.getAccidentSeverityLabel(), event.getAccidentPriority());
            } catch (Exception e) {
                log.warn("事故严重程度评估失败，事件仍按默认处理: eventNo={}, error={}", eventNo, e.getMessage());
            }
        }

        alertEventMapper.insert(event);
        log.info("创建交通告警事件: eventNo={}, type={}, camera={}, level={}",
                eventNo, request.getEventType(), camera.getCameraName(), event.getEventLevel());

        boolean isMajorAccident = accidentSeverityService.isMajorAccident(event);

        AlertWebSocket.sendAlertMessage(event, isMajorAccident);
        notificationService.sendAlertNotification(event, isMajorAccident);

        if (isMajorAccident) {
            log.warn("===== 重大事故优先响应开始 =====");
        }

        if (event.getEventLevel() != null && event.getEventLevel() >= 2) {
            ptzCruiseService.pauseCruiseForEvent(camera.getId());
            log.info("事件联动暂停巡航: cameraId={}, eventNo={}", camera.getId(), eventNo);
        }

        if (isMajorAccident) {
            try {
                ptzCruiseService.triggerMajorAccidentResponse(camera.getId(), event);
                log.info("重大事故专用PTZ响应已触发: cameraId={}, eventNo={}", camera.getId(), eventNo);
            } catch (Exception e) {
                log.warn("重大事故PTZ响应失败: eventNo={}, error={}", eventNo, e.getMessage());
            }
        }

        if (event.getEventLevel() != null && event.getEventLevel() >= 2) {
            autoCreateWorkOrder(event, isMajorAccident);
        }

        if (isMajorAccident) {
            log.warn("===== 重大事故优先响应完成: eventNo={} =====", eventNo);
        }

        if (request.getTrackData() != null && !request.getTrackData().isEmpty()) {
            try {
                linkEventToTracks(event, request.getTrackData(), camera);
            } catch (Exception e) {
                log.warn("关联告警事件与轨迹失败: eventNo={}, error={}", eventNo, e.getMessage());
            }
        }

        try {
            videoRecordingService.scheduleRecording(event, request);
        } catch (Exception e) {
            log.warn("调度事件视频录制失败: eventNo={}, error={}", eventNo, e.getMessage());
        }

        java.util.List<com.traffic.alert.entity.PlateRecognition> savedPlates = saveLicensePlates(event, request, camera);

        if ("REVERSE".equals(event.getEventType()) && savedPlates != null && !savedPlates.isEmpty()) {
            try {
                policePushService.pushForReverseEvent(event, savedPlates);
                log.info("逆行事件已同步交警系统推送: eventNo={}, plates={}, count={}",
                        eventNo,
                        savedPlates.stream().map(p -> p.getPlateNumber()).toList(),
                        savedPlates.size());
            } catch (Exception e) {
                log.warn("交警系统推送失败: eventNo={}, error={}", eventNo, e.getMessage());
            }
        }

        return event;
    }

    private java.util.List<com.traffic.alert.entity.PlateRecognition> saveLicensePlates(
            AlertEvent event, AiEventCallbackRequest request, Camera camera) {
        java.util.List<java.util.Map<String, Object>> plates = request.getLicensePlates();
        if (plates == null || plates.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<com.traffic.alert.entity.PlateRecognition> results = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> plateMap : plates) {
            try {
                com.traffic.alert.entity.PlateRecognition pr = new com.traffic.alert.entity.PlateRecognition();
                pr.setAlertEventId(event.getId());
                pr.setEventNo(event.getEventNo());
                pr.setCameraId(camera.getId());
                pr.setCameraName(camera.getCameraName());
                pr.setRecognizeTime(event.getEventTime());

                Object plateNumber = plateMap.get("plate_number") != null ? plateMap.get("plate_number") : plateMap.get("plateNumber");
                if (plateNumber != null) pr.setPlateNumber(plateNumber.toString());
                Object conf = plateMap.get("confidence");
                if (conf != null) {
                    pr.setConfidence(new java.math.BigDecimal(conf.toString()));
                }
                Object plateColor = plateMap.get("plate_color") != null ? plateMap.get("plate_color") : plateMap.get("plateColor");
                if (plateColor != null) pr.setPlateColor(plateColor.toString());
                Object vehicleColor = plateMap.get("vehicle_color") != null ? plateMap.get("vehicle_color") : plateMap.get("vehicleColor");
                if (vehicleColor != null) pr.setVehicleColor(vehicleColor.toString());
                Object vehicleType = plateMap.get("vehicle_type") != null ? plateMap.get("vehicle_type") : plateMap.get("vehicleType");
                if (vehicleType != null) pr.setVehicleType(vehicleType.toString());
                Object scene = plateMap.get("scene_type") != null ? plateMap.get("scene_type") : plateMap.get("sceneType");
                if (scene != null) pr.setSceneType(scene.toString());
                Object gain = plateMap.get("enhance_gain") != null ? plateMap.get("enhance_gain") : plateMap.get("enhanceGain");
                if (gain != null) pr.setEnhanceGain(new java.math.BigDecimal(gain.toString()));
                Object bbox = plateMap.get("bbox");
                if (bbox instanceof java.util.List<?> list && list.size() >= 4) {
                    try {
                        pr.setBboxX1(((Number) list.get(0)).intValue());
                        pr.setBboxY1(((Number) list.get(1)).intValue());
                        pr.setBboxX2(((Number) list.get(2)).intValue());
                        pr.setBboxY2(((Number) list.get(3)).intValue());
                    } catch (Exception ignored) {}
                }
                Object trackId = plateMap.get("track_id") != null ? plateMap.get("track_id") : plateMap.get("trackId");
                if (trackId instanceof Number n) pr.setTrackId(n.intValue());

                if (pr.getPlateNumber() != null && !pr.getPlateNumber().isEmpty()) {
                    plateRecognitionService.save(pr);
                    results.add(pr);
                }
            } catch (Exception e) {
                log.warn("保存车牌识别结果失败: eventNo={}, err={}", event.getEventNo(), e.getMessage());
            }
        }
        if (!results.isEmpty()) {
            log.info("保存车牌识别结果: eventNo={}, count={}", event.getEventNo(), results.size());
        }
        return results;
    }

    private void linkEventToTracks(AlertEvent event, List<Map<String, Object>> trackData, Camera camera) {
        int linkedCount = 0;
        for (Map<String, Object> track : trackData) {
            String plate = null;
            String className = null;
            Integer localTrackId = null;

            if (track.get("trackId") != null) {
                localTrackId = ((Number) track.get("trackId")).intValue();
            }
            if (track.get("className") != null) {
                className = track.get("className").toString();
            }

            try {
                var gt = globalTrackService.findOrCreateTrackFromEvent(
                        plate, null, className,
                        camera.getId(), camera.getCameraName(),
                        event.getEventTime()
                );
                if (gt != null) {
                    globalTrackService.linkEventToTrack(
                            event.getId(), event.getEventNo(),
                            gt.getId(), gt.getTrackNo(),
                            1, null,
                            camera.getId(), null,
                            "事件发生时自动关联"
                    );
                    linkedCount++;
                }
            } catch (Exception e) {
                log.warn("关联单条轨迹失败: trackId={}, error={}", localTrackId, e.getMessage());
            }
        }
        if (linkedCount > 0) {
            log.info("告警事件已关联{}条轨迹: eventNo={}", linkedCount, event.getEventNo());
        }
    }

    private void autoCreateWorkOrder(AlertEvent event) {
        autoCreateWorkOrder(event, false);
    }

    private void autoCreateWorkOrder(AlertEvent event, boolean isMajor) {
        try {
            int deptType = "ACCIDENT".equals(event.getEventType()) ? (isMajor ? 3 : 2) : 1;
            Department nearestDept = departmentService.findNearestDepartment(
                    event.getLongitude(), event.getLatitude(), deptType
            );

            if (nearestDept != null) {
                WorkOrder workOrder = new WorkOrder();
                workOrder.setOrderNo("WO" + LocalDateTime.now().format(EVENT_NO_FORMATTER) +
                        String.format("%04d", (int) (Math.random() * 10000)));
                workOrder.setAlertEventId(event.getId());
                workOrder.setEventType(event.getEventType());
                workOrder.setDebrisCategory(event.getDebrisCategory());
                workOrder.setOrderLevel(event.getEventLevel());

                String typeText = switch (event.getEventType()) {
                    case "ACCIDENT" -> {
                        if (event.getAccidentSeverityLabel() != null) {
                            yield event.getAccidentSeverityLabel();
                        }
                        yield "交通事故";
                    }
                    case "REVERSE" -> "车辆逆行";
                    case "DEBRIS" -> {
                        String debrisLabel = event.getDebrisCategory() != null
                                ? DebrisCategory.of(event.getDebrisCategory()).getLabel()
                                : "路面抛洒物";
                        yield debrisLabel;
                    }
                    case "INTRUSION" -> "区域入侵";
                    default -> event.getEventType();
                };
                String majorPrefix = isMajor ? "【紧急】" : "";
                workOrder.setTitle(majorPrefix + typeText + "处置工单 - " + event.getCameraName());

                StringBuilder desc = new StringBuilder();
                if (event.getDescription() != null) desc.append(event.getDescription());
                if ("ACCIDENT".equals(event.getEventType()) && event.getAccidentSeverity() != null) {
                    desc.append("\n\n【事故特征】");
                    desc.append("\n事故等级: ").append(event.getAccidentSeverityLabel());
                    if (event.getAccidentVehicles() != null) {
                        desc.append("\n涉事车辆: ").append(event.getAccidentVehicles()).append("辆");
                    }
                    if (event.getAccidentDeformationLevel() != null) {
                        String[] defLv = {"无变形", "轻微变形", "中度变形", "重度变形", "严重报废"};
                        int lv = Math.max(0, Math.min(defLv.length - 1, event.getAccidentDeformationLevel()));
                        desc.append("\n变形程度: ").append(defLv[lv]);
                    }
                    if (event.getAccidentRollover() != null && event.getAccidentRollover() == 1) {
                        desc.append("\n是否翻滚: 是");
                    }
                    if (event.getAccidentFire() != null && event.getAccidentFire() == 1) {
                        desc.append("\n是否起火: 是");
                    }
                    if (event.getAccidentCasualty() != null && event.getAccidentCasualty() > 0) {
                        desc.append("\n人员伤亡: ").append(event.getAccidentCasualty()).append("人");
                    }
                    if (event.getAccidentImpactSpeed() != null) {
                        desc.append("\n碰撞车速: ").append(event.getAccidentImpactSpeed()).append(" km/h");
                    }
                }
                workOrder.setDescription(desc.toString());
                workOrder.setAssignDeptId(nearestDept.getId());
                workOrder.setAssignDeptName(nearestDept.getDeptName());
                workOrder.setOrderStatus(0);
                workOrder.setPlanStartTime(LocalDateTime.now());
                workOrder.setPlanEndTime(LocalDateTime.now().plusHours(isMajor ? 1 : 2));

                workOrderService.save(workOrder);
                log.info("自动生成工单: orderNo={}, dept={}, isMajor={}",
                        workOrder.getOrderNo(), nearestDept.getDeptName(), isMajor);
            }
        } catch (Exception e) {
            log.error("自动生成工单失败: {}", e.getMessage());
        }
    }

    @Transactional
    public AlertEvent markAsHandled(Long id, Long userId, String remark) {
        AlertEvent event = getById(id);
        if (event == null) {
            throw new BusinessException("告警事件不存在");
        }
        event.setAlertStatus(1);
        event.setHandleUserId(userId);
        event.setHandleTime(LocalDateTime.now());
        event.setHandleRemark(remark);
        alertEventMapper.updateById(event);

        if (event.getCameraId() != null && event.getEventLevel() != null && event.getEventLevel() >= 2) {
            ptzCruiseService.resumeCruiseAfterEvent(event.getCameraId());
            log.info("事件处理完成，恢复巡航: eventId={}, cameraId={}", id, event.getCameraId());
        }

        return event;
    }

    @Transactional
    public AlertEvent markAsFalsePositive(Long id, FalsePositiveRequest request) {
        AlertEvent event = getById(id);
        if (event == null) {
            throw new BusinessException("告警事件不存在");
        }
        event.setIsFalsePositive(1);
        event.setFalsePositiveReason(request.getReason());
        event.setAlertStatus(2);
        alertEventMapper.updateById(event);
        log.info("标记误报: eventId={}, reason={}", id, request.getReason());

        if (event.getCameraId() != null && event.getEventLevel() != null && event.getEventLevel() >= 2) {
            ptzCruiseService.resumeCruiseAfterEvent(event.getCameraId());
            log.info("误报标记完成，恢复巡航: eventId={}, cameraId={}", id, event.getCameraId());
        }

        return event;
    }

    public Map<String, Object> getStatistics() {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime weekAgo = today.minusDays(7);

        Long todayCount = alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .ge(AlertEvent::getEventTime, today));
        Long weekCount = alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .ge(AlertEvent::getEventTime, weekAgo));
        Long totalCount = alertEventMapper.selectCount(new LambdaQueryWrapper<>());

        Long accidentCount = alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getEventType, "ACCIDENT"));
        Long reverseCount = alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getEventType, "REVERSE"));
        Long debrisCount = alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getEventType, "DEBRIS"));

        Long pendingCount = alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getAlertStatus, 0));
        Long falsePositiveCount = alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getIsFalsePositive, 1));

        return Map.of(
                "todayCount", todayCount,
                "weekCount", weekCount,
                "totalCount", totalCount,
                "accidentCount", accidentCount,
                "reverseCount", reverseCount,
                "debrisCount", debrisCount,
                "pendingCount", pendingCount,
                "falsePositiveCount", falsePositiveCount
        );
    }

    public List<AlertEvent> getRecentEvents(int limit) {
        return alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .orderByDesc(AlertEvent::getEventTime)
                .last("LIMIT " + limit));
    }

    public String uploadEventSnapshot(Long eventId, MultipartFile file) {
        AlertEvent event = getById(eventId);
        if (event == null) {
            throw new BusinessException("告警事件不存在");
        }
        String objectName = "snapshots/" + event.getEventNo() + "_" + System.currentTimeMillis() + ".jpg";
        String url = minioService.uploadFile(objectName, file);
        event.setEventSnapshot(url);
        alertEventMapper.updateById(event);
        return url;
    }

    @Transactional
    public String uploadEventVideo(Long cameraId, String eventNo, MultipartFile videoFile) {
        if (videoFile == null || videoFile.isEmpty()) {
            throw new BusinessException("视频文件为空");
        }

        String fileName = videoFile.getOriginalFilename();
        String extension = fileName != null && fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf("."))
                : ".mp4";

        String finalEventNo = (eventNo != null && !eventNo.isEmpty())
                ? eventNo
                : "EVT" + LocalDateTime.now().format(EVENT_NO_FORMATTER) + String.format("%04d", (int) (Math.random() * 10000));

        String objectName = "event-videos/" + finalEventNo + extension;
        String contentType = videoFile.getContentType() != null ? videoFile.getContentType() : "video/mp4";

        String videoUrl = minioService.uploadFile(objectName, videoFile.getInputStream(),
                videoFile.getSize(), contentType);

        log.info("事件视频已上传: eventNo={}, url={}, size={}KB",
                finalEventNo, videoUrl, videoFile.getSize() / 1024);

        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertEvent::getEventNo, finalEventNo).last("LIMIT 1");
        AlertEvent existEvent = alertEventMapper.selectOne(wrapper);
        if (existEvent != null) {
            existEvent.setEventVideo(videoUrl);
            alertEventMapper.updateById(existEvent);
            log.info("已更新告警事件视频URL: eventId={}", existEvent.getId());
        }

        return videoUrl;
    }
}

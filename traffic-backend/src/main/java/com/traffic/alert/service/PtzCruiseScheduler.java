package com.traffic.alert.service;

import com.traffic.alert.entity.PtzCruisePoint;
import com.traffic.alert.vo.PtzCruiseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PtzCruiseScheduler {

    private final CameraService cameraService;

    private final Map<Long, Future<?>> cruiseFutures = new ConcurrentHashMap<>();
    private final Map<Long, PtzCruiseVO> cruiseMap = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> pausedMap = new ConcurrentHashMap<>();
    private final Map<Long, Integer> currentPointIndexMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10, r -> {
        Thread t = new Thread(r, "ptz-cruise-scheduler");
        t.setDaemon(true);
        return t;
    });

    public void startCruise(PtzCruiseVO cruise) {
        Long cruiseId = cruise.getId();
        Long cameraId = cruise.getCameraId();

        if (cruiseFutures.containsKey(cruiseId)) {
            stopCruise(cruiseId);
        }

        cruiseMap.put(cruiseId, cruise);
        pausedMap.put(cameraId, false);
        currentPointIndexMap.put(cruiseId, 0);

        Future<?> future = scheduler.submit(() -> runCruise(cruiseId));
        cruiseFutures.put(cruiseId, future);
        log.info("启动巡航调度: cruiseId={}, cameraId={}", cruiseId, cameraId);
    }

    public void stopCruise(Long cruiseId) {
        Future<?> future = cruiseFutures.remove(cruiseId);
        if (future != null) {
            future.cancel(true);
        }
        cruiseMap.remove(cruiseId);
        currentPointIndexMap.remove(cruiseId);
        log.info("停止巡航调度: cruiseId={}", cruiseId);
    }

    public boolean isCruising(Long cameraId) {
        if (cameraId == null) return false;
        return cruiseMap.values().stream()
                .anyMatch(c -> c.getCameraId().equals(cameraId) && cruiseFutures.containsKey(c.getId()));
    }

    public void pauseForEvent(Long cameraId) {
        if (cameraId == null) return;
        for (Map.Entry<Long, PtzCruiseVO> entry : cruiseMap.entrySet()) {
            if (entry.getValue().getCameraId().equals(cameraId) && entry.getValue().getEventLinkage() == 1) {
                pausedMap.put(cameraId, true);
                log.info("事件联动暂停巡航: cameraId={}", cameraId);
                break;
            }
        }
    }

    public void resumeAfterEvent(Long cameraId) {
        if (cameraId == null) return;
        PtzCruiseVO targetCruise = null;
        for (Map.Entry<Long, PtzCruiseVO> entry : cruiseMap.entrySet()) {
            if (entry.getValue().getCameraId().equals(cameraId) && entry.getValue().getEventLinkage() == 1) {
                targetCruise = entry.getValue();
                break;
            }
        }
        if (targetCruise != null && Boolean.TRUE.equals(pausedMap.get(cameraId))) {
            Integer returnSeconds = targetCruise.getEventReturnSeconds();
            if (returnSeconds != null && returnSeconds > 0) {
                scheduler.schedule(() -> {
                    pausedMap.put(cameraId, false);
                    log.info("事件结束恢复巡航: cameraId={}", cameraId);
                }, returnSeconds, TimeUnit.SECONDS);
            } else {
                pausedMap.put(cameraId, false);
            }
        }
    }

    private void runCruise(Long cruiseId) {
        PtzCruiseVO cruise = cruiseMap.get(cruiseId);
        if (cruise == null) return;

        Long cameraId = cruise.getCameraId();
        List<PtzCruisePoint> points = cruise.getPoints();
        if (points == null || points.isEmpty()) return;

        int loopCount = cruise.getLoopCount() != null ? cruise.getLoopCount() : 0;
        int currentLoop = 0;

        try {
            while (!Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < points.size(); i++) {
                    if (Thread.currentThread().isInterrupted()) return;

                    while (Boolean.TRUE.equals(pausedMap.get(cameraId))) {
                        Thread.sleep(500);
                        if (Thread.currentThread().isInterrupted()) return;
                    }

                    PtzCruisePoint point = points.get(i);
                    currentPointIndexMap.put(cruiseId, i);

                    try {
                        cameraService.gotoPreset(cameraId, point.getPresetIndex());
                        log.debug("巡航转到预置位: cruiseId={}, presetIndex={}, name={}",
                                cruiseId, point.getPresetIndex(), point.getPresetName());
                    } catch (Exception e) {
                        log.warn("巡航转到预置位失败: cruiseId={}, presetIndex={}, error={}",
                                cruiseId, point.getPresetIndex(), e.getMessage());
                    }

                    int staySeconds = point.getStaySeconds() != null ? point.getStaySeconds() :
                            (cruise.getStaySeconds() != null ? cruise.getStaySeconds() : 10);

                    for (int s = 0; s < staySeconds * 2; s++) {
                        if (Thread.currentThread().isInterrupted()) return;
                        if (Boolean.TRUE.equals(pausedMap.get(cameraId))) break;
                        Thread.sleep(500);
                    }
                }

                currentLoop++;
                if (loopCount > 0 && currentLoop >= loopCount) {
                    log.info("巡航完成{}次循环，结束: cruiseId={}", loopCount, cruiseId);
                    break;
                }
            }
        } catch (InterruptedException e) {
            log.info("巡航被中断: cruiseId={}", cruiseId);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("巡航异常: cruiseId={}", cruiseId, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("关闭PTZ巡航调度器...");
        for (Long cruiseId : cruiseFutures.keySet()) {
            stopCruise(cruiseId);
        }
        scheduler.shutdownNow();
    }
}

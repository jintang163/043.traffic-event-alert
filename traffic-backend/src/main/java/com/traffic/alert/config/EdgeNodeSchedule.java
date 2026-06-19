package com.traffic.alert.config;

import com.traffic.alert.service.EdgeNodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EdgeNodeSchedule {

    private final EdgeNodeService edgeNodeService;

    @Scheduled(fixedDelay = 120000, initialDelay = 60000)
    public void checkOnlineStatus() {
        try {
            edgeNodeService.checkOnlineStatus();
        } catch (Exception e) {
            log.error("定时检查边缘节点在线状态失败: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 5 * * * *")
    public void reprocessUnlinkedOfflineEvents() {
        try {
            int count = edgeNodeService.reprocessUnlinkedOfflineEvents();
            if (count > 0) {
                log.info("定时补关联离线事件: count={}", count);
            }
        } catch (Exception e) {
            log.error("定时补关联离线事件失败: {}", e.getMessage());
        }
    }
}

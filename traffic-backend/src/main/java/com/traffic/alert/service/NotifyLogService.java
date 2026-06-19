package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.NotifyLogQuery;
import com.traffic.alert.entity.NotifyLog;
import com.traffic.alert.mapper.NotifyLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyLogService {

    private final NotifyLogMapper notifyLogMapper;

    @Lazy
    private final NotificationService notificationService;

    private static final DateTimeFormatter LOG_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public NotifyLog getById(Long id) {
        return notifyLogMapper.selectById(id);
    }

    public PageResult<NotifyLog> page(NotifyLogQuery query) {
        Page<NotifyLog> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<NotifyLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getEventNo())) {
            wrapper.eq(NotifyLog::getEventNo, query.getEventNo());
        }
        if (StringUtils.hasText(query.getChannelType())) {
            wrapper.eq(NotifyLog::getChannelType, query.getChannelType());
        }
        if (query.getSendStatus() != null) {
            wrapper.eq(NotifyLog::getSendStatus, query.getSendStatus());
        }
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(NotifyLog::getCreateTime, LocalDateTime.parse(query.getStartTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(NotifyLog::getCreateTime, LocalDateTime.parse(query.getEndTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        wrapper.orderByDesc(NotifyLog::getCreateTime);
        notifyLogMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), query.getSize());
    }

    public NotifyLog createLog(NotifyLog logEntry) {
        if (logEntry.getLogNo() == null || logEntry.getLogNo().isEmpty()) {
            logEntry.setLogNo("NLOG" + LocalDateTime.now().format(LOG_NO_FORMATTER) +
                    String.format("%04d", (int) (Math.random() * 10000)));
        }
        if (logEntry.getSendStatus() == null) logEntry.setSendStatus(0);
        if (logEntry.getRetryCount() == null) logEntry.setRetryCount(0);
        if (logEntry.getMaxRetry() == null) logEntry.setMaxRetry(3);
        notifyLogMapper.insert(logEntry);
        return logEntry;
    }

    public void updateLog(NotifyLog logEntry) {
        notifyLogMapper.updateById(logEntry);
    }

    public List<NotifyLog> findPendingRetry() {
        return notifyLogMapper.selectList(new LambdaQueryWrapper<NotifyLog>()
                .eq(NotifyLog::getSendStatus, 3)
                .apply("retry_count < max_retry")
                .le(NotifyLog::getNextRetryTime, LocalDateTime.now())
                .isNotNull(NotifyLog::getNextRetryTime));
    }

    @Scheduled(fixedDelay = 30000)
    public void retryFailedNotifications() {
        List<NotifyLog> pending = findPendingRetry();
        for (NotifyLog logEntry : pending) {
            try {
                log.info("重试通知推送: logNo={}, retry={}/{}", logEntry.getLogNo(), logEntry.getRetryCount(), logEntry.getMaxRetry());
                notificationService.retryNotify(logEntry);
            } catch (Exception e) {
                log.error("重试通知推送异常: logNo={}, error={}", logEntry.getLogNo(), e.getMessage());
            }
        }
    }

    public void manualRetry(Long id) {
        NotifyLog logEntry = getById(id);
        if (logEntry == null) return;
        if (logEntry.getSendStatus() == 2) return;
        notificationService.retryNotify(logEntry);
    }
}

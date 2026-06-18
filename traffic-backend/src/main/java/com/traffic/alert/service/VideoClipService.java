package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.VideoClipQuery;
import com.traffic.alert.entity.VideoClip;
import com.traffic.alert.mapper.VideoClipMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoClipService {

    private final VideoClipMapper videoClipMapper;
    private final MinioService minioService;

    public VideoClip getById(Long id) {
        VideoClip clip = videoClipMapper.selectById(id);
        if (clip != null) {
            refreshPresignedUrls(clip);
        }
        return clip;
    }

    public PageResult<VideoClip> page(VideoClipQuery query) {
        Page<VideoClip> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<VideoClip> wrapper = new LambdaQueryWrapper<>();

        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(VideoClip::getEventNo, query.getKeyword())
                    .or().like(VideoClip::getCameraName, query.getKeyword());
        }
        if (query.getCameraId() != null) {
            wrapper.eq(VideoClip::getCameraId, query.getCameraId());
        }
        if (query.getAlertEventId() != null) {
            wrapper.eq(VideoClip::getAlertEventId, query.getAlertEventId());
        }
        if (query.getClipType() != null && !query.getClipType().isEmpty()) {
            wrapper.eq(VideoClip::getClipType, query.getClipType());
        }
        if (query.getEventType() != null && !query.getEventType().isEmpty()) {
            wrapper.eq(VideoClip::getClipType, query.getEventType());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(VideoClip::getStartTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(VideoClip::getEndTime, query.getEndTime());
        }

        wrapper.orderByDesc(VideoClip::getStartTime);

        videoClipMapper.selectPage(page, wrapper);

        page.getRecords().forEach(this::refreshPresignedUrls);

        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
    }

    public List<VideoClip> listByEvent(Long eventId) {
        LambdaQueryWrapper<VideoClip> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoClip::getAlertEventId, eventId)
                .orderByDesc(VideoClip::getCreateTime);
        List<VideoClip> list = videoClipMapper.selectList(wrapper);
        list.forEach(this::refreshPresignedUrls);
        return list;
    }

    public VideoClip save(VideoClip clip) {
        if (clip.getId() == null) {
            videoClipMapper.insert(clip);
            log.info("创建视频片段记录: id={}, eventNo={}", clip.getId(), clip.getEventNo());
        } else {
            videoClipMapper.updateById(clip);
        }
        return clip;
    }

    public VideoClip update(VideoClip clip) {
        videoClipMapper.updateById(clip);
        return clip;
    }

    public void markRecording(Long id) {
        VideoClip clip = new VideoClip();
        clip.setId(id);
        clip.setRecordStatus(1);
        videoClipMapper.updateById(clip);
    }

    public void markFailed(Long id, String reason) {
        VideoClip clip = new VideoClip();
        clip.setId(id);
        clip.setRecordStatus(-1);
        clip.setFailReason(StringUtils.hasText(reason) && reason.length() <= 500
                ? reason : reason.substring(0, Math.min(reason.length(), 497)) + "...");
        videoClipMapper.updateById(clip);
        log.warn("视频录制失败: id={}, reason={}", id, reason);
    }

    public void delete(Long id) {
        VideoClip clip = videoClipMapper.selectById(id);
        if (clip == null) return;

        if (clip.getFilePath() != null) {
            try {
                minioService.deleteFile(clip.getFilePath());
            } catch (Exception ignored) {}
        }
        if (clip.getHlsPlaylistPath() != null) {
            try {
                minioService.deleteFile(clip.getHlsPlaylistPath());
            } catch (Exception ignored) {}
        }
        if (clip.getThumbnailUrl() != null && clip.getThumbnailUrl().contains("/")) {
            try {
                int idx = clip.getThumbnailUrl().indexOf("videos/");
                if (idx > 0) {
                    String obj = clip.getThumbnailUrl().substring(idx);
                    minioService.deleteFile(obj);
                }
            } catch (Exception ignored) {}
        }

        videoClipMapper.deleteById(id);
        log.info("删除视频片段: id={}", id);
    }

    private void refreshPresignedUrls(VideoClip clip) {
        try {
            if (clip.getHlsPlaylistPath() != null) {
                clip.setHlsPlaylistUrl(minioService.getPresignedUrl(clip.getHlsPlaylistPath(), 24, TimeUnit.HOURS));
            }
            if (clip.getFilePath() != null) {
                clip.setFileUrl(minioService.getPresignedUrl(clip.getFilePath(), 24, TimeUnit.HOURS));
            }
        } catch (Exception e) {
            log.debug("刷新预签名URL失败: clipId={}, err={}", clip.getId(), e.getMessage());
        }
    }

    public long getStorageBytes() {
        try {
            Long total = videoClipMapper.selectList(new LambdaQueryWrapper<VideoClip>()
                            .select(VideoClip::getFileSize))
                    .stream()
                    .filter(c -> c.getFileSize() != null)
                    .mapToLong(VideoClip::getFileSize)
                    .sum();
            return total;
        } catch (Exception e) {
            return 0L;
        }
    }

    public int getTodayCount() {
        LocalDateTime start = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        return Math.toIntExact(videoClipMapper.selectCount(new LambdaQueryWrapper<VideoClip>()
                .ge(VideoClip::getCreateTime, start)));
    }
}

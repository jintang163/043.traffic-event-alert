package com.traffic.alert.service;

import com.traffic.alert.config.VideoConfig;
import com.traffic.alert.dto.AiEventCallbackRequest;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.entity.VideoClip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoRecordingService {

    private final VideoConfig videoConfig;
    private final MinioService minioService;
    private final CameraService cameraService;
    private final VideoClipService videoClipService;

    private final ExecutorService recordingExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "video-recorder-" + COUNTER.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final DateTimeFormatter DIR_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public VideoClip scheduleRecording(AlertEvent event, AiEventCallbackRequest request) {
        if (!Boolean.TRUE.equals(videoConfig.getEnabled())) {
            log.info("视频录制功能已关闭，跳过: eventNo={}", event.getEventNo());
            return null;
        }

        Camera camera = cameraService.getById(event.getCameraId());
        if (camera == null) {
            log.warn("摄像头不存在，无法录制视频: cameraId={}", event.getCameraId());
            return null;
        }

        VideoClip clip = createInitialClip(event, camera);
        videoClipService.save(clip);

        CompletableFuture.runAsync(() -> {
            try {
                doRecord(clip, event, camera, request);
            } catch (Exception e) {
                log.error("视频录制异常: eventNo={}, clipId={}, error={}", event.getEventNo(), clip.getId(), e.getMessage(), e);
                videoClipService.markFailed(clip.getId(), e.getMessage());
            }
        }, recordingExecutor).exceptionally(e -> {
            log.error("录制任务未捕获异常: {}", e.getMessage(), e);
            videoClipService.markFailed(clip.getId(), e.getMessage());
            return null;
        });

        return clip;
    }

    private VideoClip createInitialClip(AlertEvent event, Camera camera) {
        VideoClip clip = new VideoClip();
        clip.setCameraId(camera.getId());
        clip.setCameraName(camera.getCameraName());
        clip.setAlertEventId(event.getId());
        clip.setEventNo(event.getEventNo());
        clip.setClipType(event.getEventType());
        clip.setStartTime(event.getEventTime() != null
                ? event.getEventTime().minusSeconds(videoConfig.getPreEventSeconds())
                : LocalDateTime.now().minusSeconds(videoConfig.getPreEventSeconds()));
        clip.setEndTime(clip.getStartTime().plusSeconds(
                videoConfig.getPreEventSeconds() + videoConfig.getPostEventSeconds()));
        clip.setDuration(videoConfig.getPreEventSeconds() + videoConfig.getPostEventSeconds());
        clip.setRecordStatus(0);
        return clip;
    }

    private void doRecord(VideoClip clip, AlertEvent event, Camera camera, AiEventCallbackRequest request) throws Exception {
        videoClipService.markRecording(clip.getId());

        ensureTempDir();

        String dateDir = LocalDateTime.now().format(DIR_FMT);
        String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String sessionName = String.format("%s_%s_%s", camera.getId(), TS_FMT.format(LocalDateTime.now()), jobId);

        Path sessionDir = Paths.get(videoConfig.getTempDir(), dateDir, sessionName);
        Files.createDirectories(sessionDir);

        String playlistName = "playlist.m3u8";
        String mp4Name = "event.mp4";
        Path mp4Path = sessionDir.resolve(mp4Name);
        Path playlistPath = sessionDir.resolve(playlistName);

        boolean sourceAvailable = false;
        String source = null;
        boolean isUrlSource = false;

        if (request != null && request.getEventVideo() != null && !request.getEventVideo().isEmpty()) {
            source = request.getEventVideo();
            isUrlSource = source.startsWith("http://") || source.startsWith("https://")
                    || source.startsWith("rtsp://") || source.startsWith("rtmp://");
            sourceAvailable = true;
            log.info("使用AI提供的事件视频源: {}", isUrlSource ? "[URL]" : "[本地文件]");
        } else if (camera.getStreamUrl() != null && !camera.getStreamUrl().isEmpty()) {
            source = camera.getStreamUrl();
            isUrlSource = true;
            sourceAvailable = true;
            log.info("使用摄像头RTSP流录制: {}", camera.getStreamUrl());
        }

        if (!sourceAvailable) {
            videoClipService.markFailed(clip.getId(), "无可用视频源（AI未提供eventVideo且摄像头无streamUrl）");
            return;
        }

        Path thumbnailPath = sessionDir.resolve("thumbnail.jpg");

        boolean ffmpegOk = runFfmpegRecording(source, mp4Path, playlistPath, thumbnailPath, request != null && request.getEventVideo() != null);

        if (!ffmpegOk) {
            videoClipService.markFailed(clip.getId(), "ffmpeg执行失败，请检查ffmpeg是否可用及视频源是否可访问");
            cleanupDir(sessionDir.toFile());
            return;
        }

        String minioPrefix = String.format("videos/%s/%s", dateDir, sessionName);

        int uploaded = minioService.uploadDirectory(minioPrefix, sessionDir.toFile());
        log.info("上传MinIO完成: clipId={}, files={}", clip.getId(), uploaded);

        String playlistObj = minioPrefix + "/" + playlistName;
        String mp4Obj = minioPrefix + "/" + mp4Name;
        String thumbObj = minioPrefix + "/thumbnail.jpg";

        clip.setFileName(mp4Name);
        clip.setFilePath(mp4Obj);
        clip.setFileUrl(minioService.getPresignedUrl(mp4Obj, 24, TimeUnit.HOURS));
        clip.setHlsPlaylistPath(playlistObj);
        clip.setHlsPlaylistUrl(minioService.getPresignedUrl(playlistObj, 24, TimeUnit.HOURS));
        clip.setThumbnailUrl(minioService.getPresignedUrl(thumbObj, 24 * 7, TimeUnit.HOURS));

        if (mp4Path.toFile().exists()) {
            clip.setFileSize(mp4Path.toFile().length());
        }

        if (Files.exists(playlistPath)) {
            int segments = (int) Files.lines(playlistPath).filter(l -> l.trim().endsWith(".ts")).count();
            int estimatedDuration = segments * videoConfig.getSegmentDuration();
            if (estimatedDuration > 0) {
                clip.setDuration(estimatedDuration);
            }
        }

        clip.setRecordStatus(2);
        videoClipService.update(clip);

        log.info("视频录制完成: eventNo={}, clipId={}, duration={}s, size={}KB",
                event.getEventNo(), clip.getId(), clip.getDuration(),
                clip.getFileSize() != null ? clip.getFileSize() / 1024 : 0);

        cleanupDir(sessionDir.toFile());
    }

    private boolean runFfmpegRecording(String input, Path mp4Out, Path hlsPlaylist, Path thumbnailOut, boolean useExistingSource) {
        try {
            ProcessBuilder pb = buildFfmpegCommand(input, mp4Out.toString(), hlsPlaylist.toString(), thumbnailOut.toString(), useExistingSource);
            pb.redirectErrorStream(true);

            log.info("启动ffmpeg: {}", String.join(" ", pb.command()));
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int lines = 0;
                while ((line = reader.readLine()) != null) {
                    if (lines++ < 50) output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("ffmpeg执行超时（300s）");
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn("ffmpeg exit code={}, lastOutput={}", exitCode, output);
            }

            boolean hlsExists = Files.exists(hlsPlaylist);
            boolean mp4Exists = Files.exists(mp4Out);

            return hlsExists || mp4Exists;
        } catch (Exception e) {
            log.error("调用ffmpeg失败: {}", e.getMessage());
            return false;
        }
    }

    private ProcessBuilder buildFfmpegCommand(String input, String mp4Path, String hlsPath, String thumbPath, boolean useExistingSource) {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(videoConfig.getFfmpegPath());
        cmd.add("-y");
        cmd.add("-loglevel");
        cmd.add("warning");

        if (useExistingSource) {
            cmd.add("-i");
            cmd.add(input);

            cmd.add("-map");
            cmd.add("0:v:0");
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add("veryfast");
            cmd.add("-crf");
            cmd.add("28");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
            cmd.add("-movflags");
            cmd.add("+faststart");
            cmd.add(mp4Path);

            cmd.add("-map");
            cmd.add("0:v:0");
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add("veryfast");
            cmd.add("-crf");
            cmd.add("30");
            cmd.add("-hls_time");
            cmd.add(String.valueOf(videoConfig.getSegmentDuration()));
            cmd.add("-hls_list_size");
            cmd.add(String.valueOf(videoConfig.getHlsListSize()));
            cmd.add("-hls_flags");
            cmd.add("independent_segments");
            cmd.add("-hls_segment_filename");
            cmd.add(hlsPath.replace(".m3u8", "_%03d.ts"));
            cmd.add(hlsPath);

            cmd.add("-map");
            cmd.add("0:v:0");
            cmd.add("-frames:v");
            cmd.add("1");
            cmd.add("-vf");
            cmd.add("select=eq(n\\,0),scale=320:-1");
            cmd.add(thumbPath);
        } else {
            int totalSec = videoConfig.getPreEventSeconds() + videoConfig.getPostEventSeconds();
            cmd.add("-rtsp_transport");
            cmd.add("tcp");
            cmd.add("-stimeout");
            cmd.add("10000000");
            cmd.add("-i");
            cmd.add(input);
            cmd.add("-t");
            cmd.add(String.valueOf(totalSec));

            cmd.add("-map");
            cmd.add("0:v:0");
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add("veryfast");
            cmd.add("-crf");
            cmd.add("28");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
            cmd.add("-movflags");
            cmd.add("+faststart");
            cmd.add(mp4Path);

            cmd.add("-map");
            cmd.add("0:v:0");
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add("veryfast");
            cmd.add("-crf");
            cmd.add("30");
            cmd.add("-hls_time");
            cmd.add(String.valueOf(videoConfig.getSegmentDuration()));
            cmd.add("-hls_list_size");
            cmd.add(String.valueOf(videoConfig.getHlsListSize()));
            cmd.add("-hls_flags");
            cmd.add("independent_segments");
            cmd.add("-hls_segment_filename");
            cmd.add(hlsPath.replace(".m3u8", "_%03d.ts"));
            cmd.add(hlsPath);

            cmd.add("-map");
            cmd.add("0:v:0");
            cmd.add("-frames:v");
            cmd.add("1");
            cmd.add("-vf");
            cmd.add("select=eq(n\\,10),scale=320:-1");
            cmd.add(thumbPath);
        }

        return new ProcessBuilder(cmd);
    }

    private void ensureTempDir() throws Exception {
        Path temp = Paths.get(videoConfig.getTempDir());
        if (!Files.exists(temp)) {
            Files.createDirectories(temp);
            log.info("创建视频临时目录: {}", temp.toAbsolutePath());
        }
    }

    private void cleanupDir(File dir) {
        if (dir == null || !dir.exists()) return;
        try {
            Files.walk(dir.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(f -> {
                        try {
                            if (!f.delete() && f.exists()) {
                                f.deleteOnExit();
                            }
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception e) {
            log.debug("清理临时目录失败: {}", dir.getAbsolutePath());
        }
    }

    public boolean isFfmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(videoConfig.getFfmpegPath(), "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}

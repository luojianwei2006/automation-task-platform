package com.task.platform.admin.service;

import com.task.platform.admin.dto.publish.MergePreviewReq;
import com.task.platform.admin.dto.publish.MergeResultVO;
import com.task.platform.admin.entity.PublishMaterial;
import com.task.platform.admin.entity.PublishMergeHistory;
import com.task.platform.admin.mapper.PublishMaterialMapper;
import com.task.platform.admin.mapper.PublishMergeHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 视频合并预览服务
 * 将项目视频素材按 sortOrder 顺序合并，叠加背景音乐（循环播放）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MergePreviewService {

    private final PublishMaterialMapper publishMaterialMapper;
    private final PublishMergeHistoryMapper publishMergeHistoryMapper;

    /** 上传根目录 */
    private static final String UPLOAD_ROOT = "/Users/luojianwei/Documents/Workbuddy/automation_project/uploads/publish/";

    /**
     * 执行视频合并预览（异步后台处理）
     */
    public MergeResultVO mergePreview(MergePreviewReq req) {
        Long projectId = req.getProjectId();
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("projectId 不能为空");
        }

        // 检查是否有正在处理的合并任务
        long processingCount = publishMergeHistoryMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PublishMergeHistory>()
                .eq(PublishMergeHistory::getProjectId, projectId)
                .in(PublishMergeHistory::getStatus, "PENDING", "PROCESSING")
        );
        if (processingCount > 0) {
            throw new IllegalStateException("该项目有合并任务正在处理中，请等待完成");
        }

        // 1. 获取视频素材
        List<PublishMaterial> sortedVideos;
        if (req.getVideoIds() != null && !req.getVideoIds().isEmpty()) {
            sortedVideos = new ArrayList<>();
            for (Long id : req.getVideoIds()) {
                PublishMaterial m = publishMaterialMapper.selectById(id);
                if (m != null) {
                    sortedVideos.add(m);
                }
            }
            log.info("[MergePreview] 客户端指定 videoIds={}, 匹配到 {} 个素材", req.getVideoIds(), sortedVideos.size());
            if (sortedVideos.isEmpty()) {
                throw new IllegalStateException("指定的视频素材不存在或已删除");
            }
        } else {
            sortedVideos = loadMaterials(projectId, "video");
            if (sortedVideos.isEmpty()) {
                throw new IllegalStateException("该项目没有视频素材");
            }
        }

        // 2. 查询背景音乐
        PublishMaterial music = null;
        if (req.getMusicId() != null && req.getMusicId() > 0) {
            music = publishMaterialMapper.selectById(req.getMusicId());
        } else {
            List<PublishMaterial> musics = loadMaterials(projectId, "music");
            if (!musics.isEmpty()) {
                music = musics.get(new Random().nextInt(musics.size()));
            }
        }

        // 3. 创建 PENDING 记录
        PublishMergeHistory history = new PublishMergeHistory();
        history.setProjectId(projectId);
        if (req.getVideoIds() != null && !req.getVideoIds().isEmpty()) {
            history.setVideoIds(req.getVideoIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        }
        history.setMusicId(req.getMusicId());
        history.setStatus("PENDING");
        history.setOutputUrl("");  // 合并中尚无线索，异步完成后回填
        publishMergeHistoryMapper.insert(history);

        // 4. 异步执行合并
        final PublishMaterial finalMusic = music;
        final String transition = req.getTransition() != null ? req.getTransition() : "none";
        final Double transitionDuration = req.getTransitionDuration() != null ? req.getTransitionDuration() : 0.5;
        final Boolean fadeInOut = req.getFadeInOut() != null && req.getFadeInOut();
        final String subtitle = req.getSubtitle();
        new Thread(() -> doMergeAsync(history.getId(), sortedVideos, finalMusic,
                transition, transitionDuration, fadeInOut, subtitle)).start();

        log.info("[MergePreview] 异步合并已提交，historyId={}", history.getId());
        return new MergeResultVO(null, null, null, history.getId());
    }

    /**
     * 异步执行合并
     */
    private void doMergeAsync(Long historyId, List<PublishMaterial> videos, PublishMaterial music,
                               String transition, Double transitionDuration,
                               Boolean fadeInOut, String subtitle) {
        PublishMergeHistory history = publishMergeHistoryMapper.selectById(historyId);
        try {
            history.setStatus("PROCESSING");
            publishMergeHistoryMapper.updateById(history);

            String outputUrl = doMerge(videos, music, transition, transitionDuration, fadeInOut, subtitle);
            Path outputPath = Paths.get(UPLOAD_ROOT, Paths.get(outputUrl).getFileName().toString());

            Integer durationSeconds = null;
            Long fileSize = null;
            try {
                fileSize = Files.size(outputPath);
                Process p = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", outputPath.toAbsolutePath().toString()).start();
                String durStr = new String(p.getInputStream().readAllBytes()).trim();
                p.waitFor();
                if (!durStr.isEmpty()) durationSeconds = (int) Math.round(Double.parseDouble(durStr));
            } catch (Exception e) { log.warn("获取文件信息失败", e); }

            history.setOutputUrl(outputUrl);
            history.setDurationSeconds(durationSeconds);
            history.setFileSize(fileSize);
            history.setStatus("COMPLETED");
            publishMergeHistoryMapper.updateById(history);
            log.info("[MergePreview] 合并完成: historyId={}, url={}", historyId, outputUrl);
        } catch (Exception e) {
            log.error("[MergePreview] 合并失败: historyId={}", historyId, e);
            history.setStatus("FAILED");
            history.setErrorMessage(e.getMessage());
            publishMergeHistoryMapper.updateById(history);
        }
    }

    /**
     * 核心 FFmpeg 合并逻辑
     */
    private String doMerge(List<PublishMaterial> videos, PublishMaterial music,
                           String transition, Double transitionDuration,
                           Boolean fadeInOut, String subtitle) {
        // 输出文件名
        String outputName = "merge_" + UUID.randomUUID().toString().replace("-", "") + ".mp4";
        Path outputPath = Paths.get(UPLOAD_ROOT, outputName);
        String outputUrl = "/uploads/publish/" + outputName;

        String tx = transition != null ? transition : "none";
        double td = transitionDuration != null ? transitionDuration : 0.5;
        boolean fade = fadeInOut != null && fadeInOut;

        try {
            // 构建 FFmpeg 命令
            List<String> cmd = new ArrayList<>();
            cmd.add("ffmpeg");
            cmd.add("-y");

            // 逐个添加视频输入
            int n = videos.size();
            boolean hasTransition = !"none".equals(tx) && n > 1;
            for (PublishMaterial v : videos) {
                String filePath = UPLOAD_ROOT + Paths.get(v.getFileUrl()).getFileName();
                cmd.add("-i");
                cmd.add(filePath);
            }

            // 预取每个视频的实际时长（秒），用于 xfade offset 和 fade-out 位置
            double[] videoDurations = new double[n];
            for (int i = 0; i < n; i++) {
                String fp = UPLOAD_ROOT + Paths.get(videos.get(i).getFileUrl()).getFileName();
                try {
                    Process p = new ProcessBuilder("ffprobe", "-v", "error",
                        "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", fp).start();
                    String dur = new String(p.getInputStream().readAllBytes()).trim();
                    p.waitFor();
                    videoDurations[i] = !dur.isEmpty() ? Double.parseDouble(dur) : 5.0;
                } catch (Exception e) {
                    videoDurations[i] = videos.get(i).getDuration() != null ? videos.get(i).getDuration() : 5.0;
                }
            }

            // 构建 filter_complex
            StringBuilder filter = new StringBuilder();

            // 1. 缩放所有视频到 1280x720，统一 timebase 避免 xfade 报错
            for (int i = 0; i < n; i++) {
                filter.append("[").append(i).append(":v]scale=1280:720,setsar=1,settb=1/30,fps=30,format=yuv420p[v").append(i).append("];");
            }

            if (hasTransition) {
                // xfade offset = 第一个视频时长 - 转场时长（让转场发生在第一个视频末尾）
                double offset = Math.max(videoDurations[0] - td, 0);
                filter.append("[v0][v1]xfade=transition=").append(tx)
                      .append(":duration=").append(td).append(":offset=").append(offset).append("[x0];");
                for (int i = 2; i < n; i++) {
                    filter.append("[x").append(i - 2).append("][v").append(i).append("]xfade=transition=")
                          .append(tx).append(":duration=").append(td).append(":offset=0[x").append(i - 1).append("];");
                }
                // 音频用 concat
                for (int i = 0; i < n; i++) filter.append("[").append(i).append(":a]");
                filter.append("concat=n=").append(n).append(":v=0:a=1[outa];");
            } else {
                // 无转场：视频+音频一起 concat
                for (int i = 0; i < n; i++) {
                    filter.append("[v").append(i).append("]").append("[").append(i).append(":a]");
                }
                filter.append("concat=n=").append(n).append(":v=1:a=1[outv][outa];");
            }

            // 2. 渐入渐出（2秒 = 60帧@30fps）
            String videoSrc = hasTransition ? "[x" + (n - 2) + "]" : "[outv]";
            if (fade) {
                // 总帧数 = 各视频秒数 × 30 - xfade 重叠帧
                int totalFrames = 0;
                for (double d : videoDurations) {
                    totalFrames += (int) Math.round(d * 30);
                }
                if (hasTransition) {
                    totalFrames -= (int) ((n - 1) * td * 30);
                }
                int fadeOutStart = Math.max(totalFrames - 60, 0);
                filter.append(videoSrc).append("fade=in:0:60,fade=out:").append(fadeOutStart).append(":60[vfaded];");
                videoSrc = "[vfaded]";
            }

            // 3. 字幕
            if (subtitle != null && !subtitle.isEmpty()) {
                filter.append(videoSrc).append("drawtext=text='").append(subtitle.replace("'", "\\'"))
                      .append("':fontsize=36:fontcolor=white@0.8:borderw=2:x=(w-tw)/2:y=h-th-20[vtext];");
                videoSrc = "[vtext]";
            }

            // 最终视频标签名（去掉方括号，用于 -map）
            String videoLabel = videoSrc.substring(1, videoSrc.length() - 1);

            String filterStr = filter.toString();

            if (music != null) {
                // 有背景音乐
                String musicPath = UPLOAD_ROOT + Paths.get(music.getFileUrl()).getFileName();
                cmd.add("-stream_loop");
                cmd.add("-1");
                cmd.add("-i");
                cmd.add(musicPath);
                int audioIdx = n;
                cmd.add("-filter_complex");
                cmd.add(filterStr + "[outa][" + audioIdx + ":a]amix=inputs=2:duration=first[finala]");
                cmd.add("-map");
                cmd.add("[" + videoLabel + "]");
                cmd.add("-map");
                cmd.add("[finala]");
                cmd.add("-shortest");
            } else {
                // 无背景音乐
                cmd.add("-filter_complex");
                cmd.add(filterStr);
                cmd.add("-map");
                cmd.add("[" + videoLabel + "]");
                cmd.add("-map");
                cmd.add("[outa]");
            }

            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
            cmd.add("-profile:v");
            cmd.add("high");
            cmd.add("-level:v");
            cmd.add("4.1");
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add(outputPath.toAbsolutePath().toString());

            log.info("[MergePreview] 共 {} 个视频参与合并", n);
            log.info("[MergePreview] FFmpeg 命令: {}", String.join(" ", cmd));

            // 执行 FFmpeg
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true); // 合并 stderr 到 stdout，避免死锁
            Process process = pb.start();

            // 先读取全部输出，再 waitFor（防止缓冲区填满阻塞）
            String fullOutput;
            try (java.io.InputStream is = process.getInputStream()) {
                fullOutput = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("[MergePreview] FFmpeg 执行失败: exitCode={}\n{}", exitCode, fullOutput);
                throw new RuntimeException("视频合并失败，FFmpeg 退出码: " + exitCode);
            } else {
                log.info("[MergePreview] FFmpeg 输出:\n{}", fullOutput);
            }

            log.info("[MergePreview] 合并完成: {}", outputUrl);
            return outputUrl;

        } catch (IOException | InterruptedException e) {
            log.error("[MergePreview] 合并异常", e);
            throw new RuntimeException("视频合并异常: " + e.getMessage());
        }
    }

    /**
     * 查询项目素材（按 type 筛选，未删除的）
     */
    private List<PublishMaterial> loadMaterials(Long projectId, String type) {
        return publishMaterialMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PublishMaterial>()
                        .eq(PublishMaterial::getProjectId, projectId)
                        .eq(PublishMaterial::getDeleted, 0)
                        .eq(PublishMaterial::getType, type)
                        .orderByAsc(PublishMaterial::getSortOrder)
                        .orderByAsc(PublishMaterial::getCreatedAt)
        );
    }
}

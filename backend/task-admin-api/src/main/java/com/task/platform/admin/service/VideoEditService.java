package com.task.platform.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.platform.admin.dto.publish.*;
import com.task.platform.admin.entity.VideoEditTask;
import com.task.platform.admin.enums.FilterPreset;
import com.task.platform.admin.mapper.VideoEditTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 视频编辑服务
 * 将 EditInstruction 编译为 FFmpeg filter_complex 并执行最终渲染。
 * 复用 MergePreviewService 的本地 UPLOAD_ROOT 与异步渲染范式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoEditService {

    private final VideoEditTaskMapper videoEditTaskMapper;
    private final ObjectMapper objectMapper;

    private static final String UPLOAD_ROOT = "/Users/luojianwei/Documents/Workbuddy/automation_project/uploads/publish/";

    /**
     * 提交编辑任务（异步）
     */
    public VideoEditResultVO submit(VideoEditReq req) {
        if (req == null || req.getInstruction() == null
                || req.getInstruction().getTimeline() == null
                || req.getInstruction().getTimeline().getSegments() == null
                || req.getInstruction().getTimeline().getSegments().isEmpty()) {
            throw new IllegalArgumentException("编辑指令不能为空且至少包含一个片段");
        }

        VideoEditTask task = new VideoEditTask();
        task.setProjectId(req.getProjectId());
        try {
            task.setInstructionJson(objectMapper.writeValueAsString(req.getInstruction()));
        } catch (Exception e) {
            throw new IllegalArgumentException("编辑指令序列化失败: " + e.getMessage());
        }
        task.setStatus("PENDING");
        task.setResultUrl("");
        videoEditTaskMapper.insert(task);

        new Thread(() -> doRenderAsync(task.getId())).start();
        log.info("[VideoEdit] 异步编辑已提交, taskId={}", task.getId());
        return new VideoEditResultVO(task.getId(), null, null, null);
    }

    /**
     * 查询任务结果
     */
    public VideoEditTask getTask(Long taskId) {
        return videoEditTaskMapper.selectById(taskId);
    }

    private void doRenderAsync(Long taskId) {
        VideoEditTask task = videoEditTaskMapper.selectById(taskId);
        try {
            task.setStatus("PROCESSING");
            videoEditTaskMapper.updateById(task);

            VideoEditInstruction instr = objectMapper.readValue(task.getInstructionJson(), VideoEditInstruction.class);
            String outputUrl = render(instr);

            Path outputPath = Paths.get(UPLOAD_ROOT, Paths.get(outputUrl).getFileName().toString());
            Integer durationSeconds = null;
            Long fileSize = null;
            try {
                fileSize = Files.size(outputPath);
                Process p = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration",
                        "-of", "default=noprint_wrappers=1:nokey=1", outputPath.toAbsolutePath().toString()).start();
                String dur = new String(p.getInputStream().readAllBytes()).trim();
                p.waitFor();
                if (!dur.isEmpty()) durationSeconds = (int) Math.round(Double.parseDouble(dur));
            } catch (Exception e) {
                log.warn("[VideoEdit] 获取文件信息失败", e);
            }

            task.setResultUrl(outputUrl);
            task.setDurationSeconds(durationSeconds);
            task.setFileSize(fileSize);
            task.setStatus("COMPLETED");
            videoEditTaskMapper.updateById(task);
            log.info("[VideoEdit] 渲染完成: taskId={}, url={}", taskId, outputUrl);
        } catch (Exception e) {
            log.error("[VideoEdit] 渲染失败: taskId={}", taskId, e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            videoEditTaskMapper.updateById(task);
        }
    }

    /**
     * 核心：将指令渲染为视频，返回访问URL
     */
    private String render(VideoEditInstruction instr) throws IOException, InterruptedException {
        List<VideoEditInstruction.SegmentDto> segments = instr.getTimeline().getSegments();
        int n = segments.size();

        // 输出分辨率
        int[] wh = resolveOutputSize(instr);
        int outW = wh[0], outH = wh[1];

        // 转场
        VideoEditInstruction.TransitionDto trans = (instr.getTimeline().getTransitions() != null
                && !instr.getTimeline().getTransitions().isEmpty())
                ? instr.getTimeline().getTransitions().get(0) : new VideoEditInstruction.TransitionDto();
        String tx = trans.getType() != null ? trans.getType() : "none";
        double td = trans.getDuration() != null ? trans.getDuration() : 0.5;
        boolean hasTransition = !"none".equalsIgnoreCase(tx) && n > 1;

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");

        // 输入：视频片段
        List<String> videoPaths = new ArrayList<>();
        double[] segDur = new double[n];      // 裁剪后的原始时长
        double[] effDur = new double[n];      // 调速后的有效时长
        for (int i = 0; i < n; i++) {
            String fp = resolveLocalPath(segments.get(i).getSrc());
            videoPaths.add(fp);
            cmd.add("-i");
            cmd.add(fp);
            double d = probeDuration(fp);
            VideoEditInstruction.TrimDto trim = segments.get(i).getTrim();
            double start = (trim != null && trim.getStart() != null) ? trim.getStart() : 0.0;
            double end = (trim != null && trim.getEnd() != null) ? trim.getEnd() : d;
            segDur[i] = Math.max(end - start, 0.1);
            double speed = segments.get(i).getSpeed() != null ? segments.get(i).getSpeed() : 1.0;
            effDur[i] = segDur[i] / speed;
        }

        // 计算各段在最终时间轴上的偏移
        double[] offsets = new double[n];
        offsets[0] = 0;
        for (int i = 1; i < n; i++) {
            offsets[i] = offsets[i - 1] + effDur[i - 1] - (hasTransition ? td : 0);
            if (offsets[i] < 0) offsets[i] = 0;
        }

        // 构建每段视频/音频滤镜
        StringBuilder filter = new StringBuilder();
        for (int i = 0; i < n; i++) {
            VideoEditInstruction.SegmentDto seg = segments.get(i);
            double speed = seg.getSpeed() != null ? seg.getSpeed() : 1.0;
            double vol = seg.getVolume() != null ? seg.getVolume() : 1.0;
            double start = (seg.getTrim() != null && seg.getTrim().getStart() != null) ? seg.getTrim().getStart() : 0.0;
            double end = (seg.getTrim() != null && seg.getTrim().getEnd() != null) ? seg.getTrim().getEnd() : segDur[i] + start;

            StringBuilder vf = new StringBuilder();
            vf.append("[").append(i).append(":v]trim=start=").append(start).append(":end=").append(end)
              .append(",setpts=PTS-STARTPTS,settb=1/30,fps=30");
            // 旋转
            Integer rotate = seg.getRotate() != null ? seg.getRotate() : 0;
            if (rotate != null && rotate % 360 != 0) {
                vf.append(",rotate=").append(Math.PI * rotate / 180.0);
            }
            // 镜像
            if (Boolean.TRUE.equals(seg.getMirror())) {
                vf.append(",hflip");
            }
            // 画幅裁剪（可选）
            if (seg.getCrop() != null && seg.getCrop().getW() != null && seg.getCrop().getH() != null) {
                vf.append(",crop=").append(seg.getCrop().getW()).append(":").append(seg.getCrop().getH())
                  .append(":").append(seg.getCrop().getX() != null ? seg.getCrop().getX() : 0)
                  .append(":").append(seg.getCrop().getY() != null ? seg.getCrop().getY() : 0);
            }
            // 缩放到输出比例（letterbox）
            vf.append(",scale=").append(outW).append(":").append(outH)
              .append(":force_original_aspect_ratio=decrease,pad=").append(outW).append(":").append(outH)
              .append(":(ow-iw)/2:(oh-ih)/2,setsar=1,format=yuv420p");
            // 滤镜预设
            String preset = FilterPreset.fromCode(seg.getFilterPreset()).getFfmpegFilter();
            if (preset != null && !preset.isEmpty()) {
                vf.append(",");
                String label = "[vf" + i + "]";
                filter.append(vf).append(preset).append(label).append(";");
                filter.append(label).append("null[vseg").append(i).append("];");
            } else {
                String label = "[vseg" + i + "]";
                filter.append(vf).append(label).append(";");
            }
            // 音频
            filter.append("[").append(i).append(":a]atrim=start=").append(start).append(":end=").append(end)
                  .append(",asetpts=PTS-STARTPTS,atempo=").append(speed).append(",volume=").append(vol)
                  .append("[aseg").append(i).append("];");
        }

        // 合并片段
        String videoSrc;
        String audioSrc;
        if (n == 1) {
            videoSrc = "[vseg0]";
            audioSrc = "[aseg0]";
        } else if (hasTransition) {
            // 视频 xfade 链
            filter.append("[vseg0][vseg1]xfade=transition=").append(tx).append(":duration=").append(td)
                  .append(":offset=").append(offsets[1]).append("[x0];");
            for (int i = 2; i < n; i++) {
                filter.append("[x").append(i - 1).append("][vseg").append(i).append("]xfade=transition=").append(tx)
                      .append(":duration=").append(td).append(":offset=").append(offsets[i]).append("[x").append(i - 1).append("];");
            }
            // 音频 acrossfade 链
            filter.append("[aseg0][aseg1]acrossfade=d=").append(td).append("[a0];");
            for (int i = 2; i < n; i++) {
                filter.append("[a").append(i - 2).append("][aseg").append(i).append("]acrossfade=d=").append(td)
                      .append("[a").append(i - 1).append("];");
            }
            videoSrc = "[x" + (n - 2) + "]";
            audioSrc = "[a" + (n - 2) + "]";
        } else {
            for (int i = 0; i < n; i++) filter.append("[vseg").append(i).append("]");
            filter.append("concat=n=").append(n).append(":v=1:a=0[outv];");
            for (int i = 0; i < n; i++) filter.append("[aseg").append(i).append("]");
            filter.append("concat=n=").append(n).append(":v=0:a=1[outa];");
            videoSrc = "[outv]";
            audioSrc = "[outa]";
        }

        // 原始音量
        double originalVol = (instr.getAudio() != null && instr.getAudio().getOriginalVolume() != null)
                ? instr.getAudio().getOriginalVolume() : 1.0;
        if (originalVol != 1.0) {
            filter.append(audioSrc).append("volume=").append(originalVol).append("[oa];");
            audioSrc = "[oa]";
        }

        // 字幕（整体字幕轨：start/end 已是整个视频的绝对秒数，不再叠加段偏移）
        List<VideoEditInstruction.SubtitleDto> subs = instr.getTimeline().getSubtitles();
        if (n > 0 && subs != null) {
            double totalDuration = offsets[n - 1] + effDur[n - 1] - (hasTransition ? td : 0);
            for (VideoEditInstruction.SubtitleDto s : subs) {
                if (s.getText() == null || s.getText().isEmpty()) continue;
                double absStart = s.getStart() != null ? s.getStart() : 0;
                double absEnd = s.getEnd() != null ? s.getEnd() : totalDuration;
                int size = s.getSize() != null ? s.getSize() : 36;
                String color = toFfColor(s.getColor());
                String yPos = "bottom".equalsIgnoreCase(s.getPosition()) ? "h-th-40"
                        : "top".equalsIgnoreCase(s.getPosition()) ? "40" : "(h-th)/2";
                String xPos = "left".equalsIgnoreCase(s.getAlign()) ? "40"
                        : "right".equalsIgnoreCase(s.getAlign()) ? "w-tw-40" : "(w-tw)/2";
                String text = s.getText().replace("'", "\\'");
                filter.append(videoSrc).append("drawtext=text='").append(text).append("':fontsize=").append(size)
                      .append(":fontcolor=").append(color).append(":borderw=2:bordercolor=black@0.5:x=").append(xPos)
                      .append(":y=").append(yPos).append(":enable='between(t,").append(absStart).append(",").append(absEnd).append(")'[vsub];");
                videoSrc = "[vsub]";
            }
        }

        // 图片叠加（PiP）
        int imgInputStart = n;
        if (instr.getTimeline().getSegments() != null) {
            for (int i = 0; i < n; i++) {
                List<VideoEditInstruction.OverlayDto> ovs = segments.get(i).getOverlays();
                if (ovs == null) continue;
                for (VideoEditInstruction.OverlayDto ov : ovs) {
                    if (ov.getSrc() == null) continue;
                    String imgPath = resolveLocalPath(ov.getSrc());
                    cmd.add("-i");
                    cmd.add(imgPath);
                    int idx = imgInputStart++;
                    int ow = ov.getW() != null ? ov.getW() : 200;
                    int oh = ov.getH() != null ? ov.getH() : 200;
                    double absStart = offsets[i] + (ov.getStart() != null ? ov.getStart() : 0);
                    double absEnd = offsets[i] + (ov.getEnd() != null ? ov.getEnd() : effDur[i]);
                    filter.append("[").append(idx).append(":v]scale=").append(ow).append(":").append(oh).append("[imgs").append(idx).append("];");
                    filter.append(videoSrc).append("overlay=").append(ov.getX() != null ? ov.getX() : 0).append(":")
                          .append(ov.getY() != null ? ov.getY() : 0)
                          .append(":enable='between(t,").append(absStart).append(",").append(absEnd).append(")'[ov").append(idx).append("];");
                    videoSrc = "[ov" + idx + "]";
                }
            }
        }

        // 背景音乐 / 配音
        int audioInputStart = imgInputStart;
        if (instr.getAudio() != null) {
            List<VideoEditInstruction.BgmDto> bgms = instr.getAudio().getBgm();
            if (bgms != null) {
                for (VideoEditInstruction.BgmDto bgm : bgms) {
                    if (bgm.getSrc() == null) continue;
                    cmd.add("-stream_loop");
                    cmd.add("-1");
                    cmd.add("-i");
                    cmd.add(resolveLocalPath(bgm.getSrc()));
                    int idx = audioInputStart++;
                    double bvol = bgm.getVolume() != null ? bgm.getVolume() : 0.3;
                    filter.append(audioSrc).append("[").append(idx).append(":a]amix=inputs=2:duration=first[a")
                          .append(idx).append("];");
                    filter.append("[a").append(idx).append("]volume=").append(bvol);
                    if (bgm.getFadeIn() != null && bgm.getFadeIn() > 0)
                        filter.append(",afade=t=in:d=").append(bgm.getFadeIn());
                    if (bgm.getFadeOut() != null && bgm.getFadeOut() > 0)
                        filter.append(",afade=t=out:d=").append(bgm.getFadeOut());
                    filter.append("[am").append(idx).append("];");
                    audioSrc = "[am" + idx + "]";
                }
            }
            List<VideoEditInstruction.VoiceoverDto> vos = instr.getAudio().getVoiceover();
            if (vos != null) {
                for (VideoEditInstruction.VoiceoverDto vo : vos) {
                    if (vo.getSrc() == null) continue;
                    cmd.add("-i");
                    cmd.add(resolveLocalPath(vo.getSrc()));
                    int idx = audioInputStart++;
                    double vvol = vo.getVolume() != null ? vo.getVolume() : 1.0;
                    long delayMs = (long) ((vo.getStart() != null ? vo.getStart() : 0) * 1000);
                    filter.append("[").append(idx).append(":a]adelay=").append(delayMs).append("|").append(delayMs)
                          .append(",volume=").append(vvol).append("[vo").append(idx).append("];");
                    filter.append(audioSrc).append("[vo").append(idx).append("]amix=inputs=2:duration=first[vm")
                          .append(idx).append("];");
                    audioSrc = "[vm" + idx + "]";
                }
            }
        }

        String videoLabel = videoSrc.substring(1, videoSrc.length() - 1);
        String audioLabel = audioSrc.substring(1, audioSrc.length() - 1);

        cmd.add("-filter_complex");
        cmd.add(filter.toString());
        cmd.add("-map");
        cmd.add("[" + videoLabel + "]");
        cmd.add("-map");
        cmd.add("[" + audioLabel + "]");
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
        cmd.add("-shortest");

        String outputName = "edit_" + UUID.randomUUID().toString().replace("-", "") + ".mp4";
        Path outputPath = Paths.get(UPLOAD_ROOT, outputName);
        cmd.add(outputPath.toAbsolutePath().toString());
        String outputUrl = "/uploads/publish/" + outputName;

        log.info("[VideoEdit] FFmpeg 命令: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String fullOutput;
        try (java.io.InputStream is = process.getInputStream()) {
            fullOutput = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("[VideoEdit] FFmpeg 失败 exitCode={}\n{}", exitCode, fullOutput);
            throw new RuntimeException("视频编辑渲染失败，FFmpeg 退出码: " + exitCode);
        }
        log.info("[VideoEdit] 渲染完成: {}", outputUrl);
        return outputUrl;
    }

    private int[] resolveOutputSize(VideoEditInstruction instr) {
        VideoEditInstruction.TimelineDto tl = instr.getTimeline();
        if (tl.getWidth() != null && tl.getHeight() != null && tl.getWidth() > 0 && tl.getHeight() > 0) {
            return new int[]{tl.getWidth(), tl.getHeight()};
        }
        String ratio = instr.getOutput() != null && instr.getOutput().getRatio() != null
                ? instr.getOutput().getRatio() : "9:16";
        switch (ratio) {
            case "1:1": return new int[]{1080, 1080};
            case "16:9": return new int[]{1920, 1080};
            default: return new int[]{1080, 1920};
        }
    }

    private String resolveLocalPath(String url) {
        if (url == null) return UPLOAD_ROOT;
        String name = Paths.get(url).getFileName().toString();
        return UPLOAD_ROOT + name;
    }

    private double probeDuration(String filePath) {
        try {
            Process p = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", filePath).start();
            String d = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            if (!d.isEmpty()) return Double.parseDouble(d);
        } catch (Exception e) {
            log.warn("[VideoEdit] 探测时长失败: {}", filePath, e);
        }
        return 5.0;
    }

    private String toFfColor(String color) {
        if (color == null || color.isEmpty()) return "white";
        if (color.startsWith("#")) {
            String hex = color.substring(1);
            if (hex.length() == 6) return "0x" + hex;
            if (hex.length() == 8) return "0x" + hex.substring(2) + hex.substring(0, 2); // rrggbbaa -> aarrggbb? 简单处理
        }
        return color;
    }
}

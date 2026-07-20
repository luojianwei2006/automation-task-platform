package com.task.platform.admin.dto.publish;

import lombok.Data;

import java.util.List;

/**
 * 视频编辑指令（与安卓端 EditInstruction / 前端 instruction 字段名保持一致）
 */
@Data
public class VideoEditInstruction {

    private TimelineDto timeline;
    private AudioDto audio;
    private OutputDto output;

    @Data
    public static class TimelineDto {
        private Integer width = 1080;
        private Integer height = 1920;
        private String background = "#000000";
        private List<SegmentDto> segments;
        private List<TransitionDto> transitions;
        // 整体字幕轨：跨所有片段的全局时间轴，start/end 为整个视频的绝对秒数（非某段内相对时间）
        private List<SubtitleDto> subtitles;
    }

    @Data
    public static class SegmentDto {
        private String assetId;
        private String src;            // 素材URL，如 /uploads/publish/xxx.mp4
        private TrimDto trim;          // 裁剪时间段
        private Integer rotate = 0;    // 旋转角度 0/90/180/270
        private Boolean mirror = false;
        private Double speed = 1.0;    // 0.5~2.0
        private CropDto crop;          // 画幅裁剪（可选）
        private String filterPreset = "none";
        private Double volume = 1.0;
        private List<OverlayDto> overlays;
    }

    @Data
    public static class TrimDto {
        private Double start = 0.0;
        private Double end;            // null 表示到结尾
    }

    @Data
    public static class CropDto {
        private Integer x = 0;
        private Integer y = 0;
        private Integer w;
        private Integer h;
    }

    @Data
    public static class SubtitleDto {
        private Double start;
        private Double end;
        private String text;
        private Integer size = 36;
        private String color = "#FFFFFF";
        private String position = "bottom"; // top/bottom/middle
        private String align = "center";     // left/center/right
    }

    @Data
    public static class OverlayDto {
        private String type = "image"; // image
        private String src;
        private Integer x = 0;
        private Integer y = 0;
        private Integer w;
        private Integer h;
        private Double start = 0.0;
        private Double end;
    }

    @Data
    public static class AudioDto {
        private Double originalVolume = 1.0;
        private List<BgmDto> bgm;
        private List<VoiceoverDto> voiceover;
    }

    @Data
    public static class BgmDto {
        private String src;
        private Double volume = 0.3;
        private Boolean loop = true;
        private Double fadeIn = 0.0;
        private Double fadeOut = 0.0;
    }

    @Data
    public static class VoiceoverDto {
        private String src;
        private Double volume = 1.0;
        private Double start = 0.0;
    }

    @Data
    public static class TransitionDto {
        private String type = "fade"; // none/fade/wipeleft/...
        private Double duration = 0.5;
    }

    @Data
    public static class OutputDto {
        private String ratio = "9:16"; // 9:16 / 1:1 / 16:9
        private String quality = "high";
    }
}

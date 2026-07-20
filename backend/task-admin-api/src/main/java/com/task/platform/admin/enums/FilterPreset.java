package com.task.platform.admin.enums;

import lombok.Getter;

/**
 * 视频滤镜预设（8 个）
 * ffmpegFilter 为应用于该段视频的 FFmpeg 视频滤镜链（输入流由调用方提供）。
 * 与安卓端 FilterPresets 枚举值必须保持一致（none/fresh/warm/film/gray/vintage/cool/jpn）。
 */
@Getter
public enum FilterPreset {
    NONE("none", ""),
    FRESH("fresh", "eq=brightness=0.06:saturation=1.25:contrast=1.05"),
    WARM("warm", "eq=brightness=0.04:contrast=1.08:saturation=1.1,colorbalance=rs=0.08:bs=-0.08"),
    FILM("film", "eq=contrast=1.2:brightness=-0.02:gamma=1.1:saturation=1.05"),
    GRAY("gray", "format=gray"),
    VINTAGE("vintage", "eq=contrast=1.1:saturation=0.85:brightness=0.03,colorbalance=rs=0.1:bs=-0.05"),
    COOL("cool", "eq=saturation=1.05,colorbalance=bs=0.1:rs=-0.08"),
    JPN("jpn", "eq=brightness=0.05:contrast=0.95:saturation=0.9");

    private final String code;
    private final String ffmpegFilter;

    FilterPreset(String code, String ffmpegFilter) {
        this.code = code;
        this.ffmpegFilter = ffmpegFilter;
    }

    public static FilterPreset fromCode(String code) {
        if (code == null || code.isEmpty()) return NONE;
        for (FilterPreset p : values()) {
            if (p.code.equalsIgnoreCase(code)) return p;
        }
        return NONE;
    }
}

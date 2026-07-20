package com.task.platform.model

/**
 * 8 个滤镜预设（code 与后端 FilterPreset 枚举必须一致）
 * 预览端用 Media3 Effect 做 GL 近似；后端用 FFmpeg 做最终真值渲染。
 */
enum class FilterPresetCode(val code: String, val label: String) {
    NONE("none", "原片"),
    FRESH("fresh", "清新"),
    WARM("warm", "暖阳"),
    FILM("film", "胶片"),
    GRAY("gray", "黑白"),
    VINTAGE("vintage", "复古"),
    COOL("cool", "冷调"),
    JPN("jpn", "日系");

    companion object {
        fun from(code: String?): FilterPresetCode =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: NONE
    }
}

/** 8 预设顺序（供 UI 横向排列） */
val FILTER_PRESET_LIST = FilterPresetCode.entries

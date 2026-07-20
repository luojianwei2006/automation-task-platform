package com.task.platform.model

/**
 * 视频编辑指令（与后端 VideoEditInstruction JSON 字段名严格一致）
 * 同一份指令驱动：安卓本地实时预览 + 后端 FFmpeg 最终渲染
 */

data class EditInstruction(
    val timeline: Timeline = Timeline(),
    val audio: Audio = Audio(),
    val output: Output = Output()
)

data class Timeline(
    val width: Int? = null,
    val height: Int? = null,
    val background: String = "#000000",
    val segments: List<Segment> = emptyList(),
    val transitions: List<Transition> = emptyList(),
    // 整体字幕轨：跨所有片段的全局时间轴，start/end 为整个视频的绝对秒数（非某段内相对时间）
    val subtitles: List<Subtitle>? = null
)

data class Segment(
    val assetId: String? = null,
    val src: String? = null,
    val trim: Trim? = null,
    val rotate: Int = 0,
    val mirror: Boolean = false,
    val speed: Double = 1.0,
    val crop: Crop? = null,
    val filterPreset: String = "none",
    val volume: Double = 1.0,
    val overlays: List<Overlay>? = null
)

data class Trim(val start: Double = 0.0, val end: Double? = null)
data class Crop(val x: Int = 0, val y: Int = 0, val w: Int? = null, val h: Int? = null)

data class Subtitle(
    val start: Double = 0.0,
    val end: Double? = null,
    val text: String = "",
    val size: Int = 36,
    val color: String = "#FFFFFF",
    val position: String = "bottom",
    val align: String = "center"
)

data class Overlay(
    val type: String = "image",
    val src: String? = null,
    val x: Int = 0,
    val y: Int = 0,
    val w: Int? = null,
    val h: Int? = null,
    val start: Double = 0.0,
    val end: Double? = null
)

data class Audio(
    val originalVolume: Double = 1.0,
    val bgm: List<Bgm>? = null,
    val voiceover: List<Voiceover>? = null
)

data class Bgm(
    val src: String? = null,
    val volume: Double = 0.3,
    val loop: Boolean = true,
    val fadeIn: Double = 0.0,
    val fadeOut: Double = 0.0
)

data class Voiceover(
    val src: String? = null,
    val volume: Double = 1.0,
    val start: Double = 0.0
)

data class Transition(val type: String = "fade", val duration: Double = 0.5)
data class Output(val ratio: String = "9:16", val quality: String = "high")

/** 提交编辑请求 */
data class VideoEditReq(val projectId: Long? = null, val instruction: EditInstruction)

/** 编辑任务轮询结果（对应后端 VideoEditTask 字段） */
data class VideoEditTaskVO(
    val id: Long = 0,
    val projectId: Long? = null,
    val instructionJson: String? = null,
    val status: String = "PENDING",
    val resultUrl: String? = null,
    val durationSeconds: Int? = null,
    val fileSize: Long? = null,
    val errorMessage: String? = null
)

/** 提交编辑任务返回（对应后端 VideoEditResultVO） */
data class VideoEditResultVO(
    val taskId: Long? = null,
    val resultUrl: String? = null,
    val durationSeconds: Int? = null,
    val fileSize: Long? = null
)

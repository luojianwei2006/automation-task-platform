package com.task.platform.ui.publish

import android.content.Context
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.ExoPlayer
import com.task.platform.model.FilterPresetCode
import kotlin.math.cos
import kotlin.math.sin

/**
 * 本地实时预览引擎
 * 用 ExoPlayer + Media3 视频特效（旋转/镜像/滤镜/调速）做所见即所得预览，不重新编码。
 * 同一份编辑参数会原样透传给后端 FFmpeg 做最终渲染。
 *
 * 注：Media3 1.2.0 没有 Hue/Saturation 类，这里用 RgbMatrix（4x4 颜色矩阵 provider）做饱和度/色相近似，
 * 预览为 GL 近似，最终像素以后端 FFmpeg 渲染为准。
 */
class PreviewEngine(context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        playWhenReady = true
        volume = 1f
    }

    private var currentUrl: String? = null

    @UnstableApi
    fun load(
        url: String,
        rotate: Int,
        mirror: Boolean,
        filter: FilterPresetCode,
        speed: Double,
        startSec: Double
    ) {
        val effects = buildEffects(rotate, mirror, filter)
        // 旋转/镜像/滤镜都属于"变换类特效"，必须重新 prepare 才会真正重绘，
        // 因此无论 URL 是否变化都重建 MediaItem + prepare，并保留当前播放位置。
        val reload = url != currentUrl
        val pos = if (reload) (startSec * 1000).toLong() else player.currentPosition
        val play = player.playWhenReady
        currentUrl = url
        player.setVideoEffects(effects)
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.seekTo(pos)
        player.playbackParameters = PlaybackParameters(speed.toFloat())
        player.playWhenReady = play
    }

    /** 调速走独立通道（不重新 prepare，拖动滑块时实时生效、无卡顿） */
    fun setSpeed(speed: Double) {
        player.playbackParameters = PlaybackParameters(speed.toFloat())
    }

    @UnstableApi
    private fun buildEffects(rotate: Int, mirror: Boolean, filter: FilterPresetCode): List<Effect> {
        val list = mutableListOf<Effect>()
        val scaleX = if (mirror) -1f else 1f
        if (rotate != 0 || mirror) {
            list.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(rotate.toFloat())
                    .setScale(scaleX, 1f)
                    .build()
            )
        }
        when (filter) {
            FilterPresetCode.NONE -> {}
            FilterPresetCode.FRESH -> {
                list.add(RgbMatrix { _, _ -> saturationMatrix(1.25f) })
                list.add(Brightness(0.06f)); list.add(contrast(1.05f))
            }
            FilterPresetCode.WARM -> {
                list.add(RgbMatrix { _, _ -> hueMatrix(-12f) })
                list.add(RgbMatrix { _, _ -> saturationMatrix(1.1f) })
                list.add(contrast(1.05f))
            }
            FilterPresetCode.FILM -> {
                list.add(contrast(1.2f)); list.add(Brightness(-0.02f))
                list.add(RgbMatrix { _, _ -> saturationMatrix(1.05f) })
            }
            FilterPresetCode.GRAY -> list.add(RgbMatrix { _, _ -> saturationMatrix(0f) })
            FilterPresetCode.VINTAGE -> {
                list.add(RgbMatrix { _, _ -> saturationMatrix(0.85f) })
                list.add(contrast(1.1f)); list.add(Brightness(0.03f))
            }
            FilterPresetCode.COOL -> {
                list.add(RgbMatrix { _, _ -> hueMatrix(12f) })
                list.add(RgbMatrix { _, _ -> saturationMatrix(1.05f) })
            }
            FilterPresetCode.JPN -> {
                list.add(Brightness(0.05f)); list.add(contrast(0.95f))
                list.add(RgbMatrix { _, _ -> saturationMatrix(0.9f) })
            }
        }
        return list
    }

    /**
     * 亮度保持的饱和度矩阵。s=1 不变，s=0 完全去色（灰度），s>1 增饱和。
     */
    private fun saturationMatrix(s: Float): FloatArray {
        val lr = 0.299f; val lg = 0.587f; val lb = 0.114f
        val sr = (1f - s) * lr
        val sg = (1f - s) * lg
        val sb = (1f - s) * lb
        return floatArrayOf(
            sr + s, sg,     sb,     0f,
            sr,     sg + s, sb,     0f,
            sr,     sg,     sb + s, 0f,
            0f,     0f,     0f,     1f
        )
    }

    /**
     * 亮度保持的色相旋转矩阵（deg，正=偏冷/蓝，负=偏暖/黄）。
     */
    private fun hueMatrix(deg: Float): FloatArray {
        val rad = deg * Math.PI / 180.0
        val c = cos(rad).toFloat()
        val si = sin(rad).toFloat()
        return floatArrayOf(
            0.213f + c * 0.787f - si * 0.213f,  0.715f - c * 0.715f - si * 0.715f,  0.072f - c * 0.072f + si * 0.928f,  0f,
            0.213f - c * 0.213f + si * 0.143f,  0.715f + c * 0.285f + si * 0.140f,  0.072f - c * 0.072f - si * 0.283f,  0f,
            0.213f - c * 0.213f - si * 0.787f,  0.715f - c * 0.715f + si * 0.715f,  0.072f + c * 0.928f + si * 0.072f,  0f,
            0f, 0f, 0f, 1f
        )
    }

    /**
     * 滤镜里的对比度用"倍数"表达（如 1.2 = 增强 20%），但 Media3 Contrast 参数是[-1,1]的系数，
     * 这里统一换算：系数 = 倍数 - 1，并夹在合法区间内，避免 IllegalArgumentException。
     */
    private fun contrast(multiplier: Float): Contrast {
        return Contrast((multiplier - 1f).coerceIn(-1f, 1f))
    }

    /** 预览播放/暂停切换，返回当前是否正在播放 */
    fun togglePlayback(): Boolean {
        val playing = player.isPlaying
        if (playing) player.pause() else player.play()
        return !playing
    }

    /** 进度条点击定位：mediaSec 是相对原始媒体的秒数（= 段内位置 + trim.start） */
    fun seekTo(mediaSec: Double) {
        player.seekTo((mediaSec * 1000).toLong())
    }

    fun release() {
        player.release()
    }
}

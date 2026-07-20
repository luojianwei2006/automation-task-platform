package com.task.platform.ui.publish

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.task.platform.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 视频缩略图 / 时长提取工具。
 *
 * 为什么要"先下载到本地再取帧"：
 * 编辑预览用的视频 URL 是走网关、带 JWT 鉴权的远程地址。
 * MediaMetadataRetriever.setDataSource(context, Uri.parse(remoteUrl)) 无法携带 Authorization 头，
 * 网关返回 401/403，导致取帧返回 null（片段一直转圈、胶片条全黑）且拿不到时长（总时长 0.0s）。
 * 这里改用带鉴权头的 OkHttp 把视频下载到应用缓存目录，再对本地文件取帧 + 读时长，一次到位。
 */
private val thumbClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val request = if (ApiClient.token.isNotEmpty())
            chain.request().newBuilder().addHeader("Authorization", "Bearer ${ApiClient.token}").build()
        else chain.request()
        chain.proceed(request)
    }
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

/**
 * 下载远程视频到缓存目录，返回本地路径。
 * 关键修复：之前只要文件存在且非空就复用，但首次下载若被中断（切后台/网络抖动）会留下
 * 截断/损坏的 .mp4，之后永远复用损坏文件 → MediaMetadataRetriever 打开失败 → 取帧返回 null
 * → 该片段永远没封面/胶片帧（用户反馈"第一个片段"缺图多因此）。现在下载后校验视频完整性，
 * 损坏则删除重下（最多重试两次），自愈缓存。
 */
suspend fun downloadVideoForThumbs(context: Context, url: String): String? = withContext(Dispatchers.IO) {
    try {
        val cacheDir = File(context.cacheDir, "video_thumbs")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val file = File(cacheDir, md5(url) + ".mp4")
        // 已有缓存：能正常打开才复用，否则删掉重下
        if (file.exists()) {
            if (file.length() > 0 && isValidVideo(file.absolutePath)) return@withContext file.absolutePath
            file.delete()
        }
        repeat(2) { attempt ->
            val resp = thumbClient.newCall(Request.Builder().url(url).build()).execute()
            if (!resp.isSuccessful) { resp.close(); return@withContext null }
            val body = resp.body ?: return@withContext null
            file.writeBytes(body.bytes())
            if (file.length() > 0 && isValidVideo(file.absolutePath)) return@withContext file.absolutePath
            file.delete() // 损坏，重试前先删
        }
        null
    } catch (_: Exception) {
        null
    }
}

/** 校验本地文件是否为可正常解析的视频（能读出正时长即视为有效）。 */
private fun isValidVideo(path: String): Boolean {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(path)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        d > 0L
    } catch (_: Exception) {
        false
    } finally {
        r.release()
    }
}

private fun md5(s: String): String {
    val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
    return BigInteger(1, d).toString(16)
}

/**
 * 对本地视频文件取：真实时长、首帧、胶片条多帧（以"全局秒"为 key）。
 * @param trimStart 段内裁剪起点（相对原始媒体秒）
 * @param speed 调速倍数（显示 rel 秒对应媒体时间 = trimStart + rel*speed）
 * @param globalStart 该段在全局时间轴上的起点（秒）
 */
suspend fun extractSegmentMedia(
    localPath: String,
    trimStart: Double,
    speed: Double,
    globalStart: Double
): SegmentMedia? = withContext(Dispatchers.IO) {
    val ret = MediaMetadataRetriever()
    try {
        ret.setDataSource(localPath)
        val durMs = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val durSec = durMs / 1000.0
        if (durSec <= 0.0) return@withContext null
        // 先抽胶片条多帧（OPTION_CLOSEST 解码最近帧，消除空档；us 夹在 [0, 媒体时长) 防越界）
        val frames = (durSec.toInt() + 1).coerceIn(1, 15)
        val film = mutableMapOf<Double, ImageBitmap>()
        val maxUs = ((durSec - 0.02) * 1_000_000L).toLong().coerceAtLeast(0L)
        repeat(frames) { k ->
            val rel = if (frames <= 1) 0.0 else k * durSec / (frames - 1)
            val gsec = globalStart + rel
            val us = ((trimStart + rel * speed) * 1_000_000L).toLong().coerceIn(0L, maxUs)
            val bmp = ret.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)?.asImageBitmap()
            if (bmp != null) film[gsec] = bmp
        }
        // 首帧：时间 0 在部分视频取不到 → 多级时间兜底；仍取不到则用任意一格胶片帧当封面，
        // 杜绝"封面为黑块/无图"（用户反馈第一个片段缺封面多因此）。
        val firstFrame = ret.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)?.asImageBitmap()
            ?: ret.getFrameAtTime(100_000L, MediaMetadataRetriever.OPTION_CLOSEST)?.asImageBitmap()
            ?: ret.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST)?.asImageBitmap()
            ?: ret.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST)?.asImageBitmap()
            ?: film.values.firstOrNull()
        SegmentMedia(durationSec = durSec, firstFrame = firstFrame, filmFrames = film)
    } catch (_: Exception) {
        null
    } finally {
        ret.release()
    }
}

data class SegmentMedia(
    val durationSec: Double,
    val firstFrame: ImageBitmap?,
    val filmFrames: Map<Double, ImageBitmap>
)

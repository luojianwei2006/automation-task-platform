package com.task.platform.ui.publish

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.task.platform.mapImageUrl
import com.task.platform.model.*
import com.task.platform.network.ApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private val Orange = Color(0xFFFF6A00)
private val Gray700 = Color(0xFF374151)
private val Gray500 = Color(0xFF6B7280)
private val SubColor = Color(0xFF42A5F5)

private fun fmt(sec: Double): String = "%.1f".format(sec)

/** 根据总时长挑选"好看"的标尺刻度步长（保证刻度不超过约 10 个） */
private fun rulerStep(total: Double): Double {
    val candidates = listOf(1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0)
    for (c in candidates) if (total / c <= 10) return c
    return 120.0
}

/**
 * 时间标尺 + 可拖动进度条（满宽映射 0..totalDuration）。
 * 单独置于横滑容器之外，避免被 horizontalScroll 拦截水平拖动手势导致"拖动不了"。
 */
@Composable
private fun ScrubberBar(
    totalDuration: Double,
    displayFrac: Double,
    enabled: Boolean,
    segBoundaries: List<Double>,
    onScrub: (Double) -> Unit,
    onScrubEnd: (Double) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val barW = maxWidth
        val barWPx = with(density) { barW.toPx() }
        val step = rulerStep(totalDuration)
        val ticks = remember(totalDuration) {
            buildList {
                var t = 0.0
                while (t <= totalDuration + 1e-6) { add(t); t += step }
            }
        }
        // —— 时间标尺（刻度 + 秒数标签）——
        Box(Modifier.fillMaxWidth().height(14.dp)) {
            ticks.forEach { tk ->
                val x = ((tk / totalDuration).coerceIn(0.0, 1.0) * barW.value).dp
                Box(Modifier.offset(x = x - 0.5.dp).width(1.dp).height(5.dp).background(Gray500))
                Text(
                    if (tk <= 0.0) "0s" else "${tk.toInt()}s",
                    fontSize = 9.sp, color = Gray500,
                    modifier = Modifier.offset(x = (x - 9.dp), y = 5.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        // —— 进度条（拖动 = 实时 scrub；跨段在松手时 reload）——
        var lastGT by remember { mutableStateOf(0.0) }
        Box(
            Modifier.fillMaxWidth().height(18.dp)
                .background(Gray500.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
                .pointerInput(enabled, totalDuration) {
                    if (!enabled) return@pointerInput
                    fun seekFromX(xPx: Float) {
                        val f = (xPx / barWPx).coerceIn(0f, 1f)
                        val gT = f * totalDuration
                        lastGT = gT
                        onScrub(gT)
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> seekFromX(offset.x) },
                        onHorizontalDrag = { change, _ -> seekFromX(change.position.x) },
                        onDragEnd = { onScrubEnd(lastGT) }
                    )
                }
        ) {
            Box(Modifier.width((displayFrac * barW.value).dp).fillMaxHeight().background(Orange, RoundedCornerShape(9.dp)))
            segBoundaries.forEach { bs ->
                val x = ((bs / totalDuration) * barW.value).dp
                Box(Modifier.offset(x = x - 0.5.dp).width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.5f)))
            }
            Box(
                Modifier.offset(x = (displayFrac * barW.value).dp - 6.dp, y = 3.dp)
                    .size(12.dp)
                    .background(Color.White, CircleShape)
                    .border(1.5.dp, Orange, CircleShape)
            )
        }
    }
}

/**
 * 视频左右两侧的竖向功能按钮（emoji 图标 + 文字，避免依赖扩展图标库）
 * 大点触区：整列 76dp 宽，按钮充满列宽，emoji 24sp + 文字 12sp。
 */
@Composable
private fun RailItem(
    emoji: String,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (active) Orange.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(12.dp)
            )
            .padding(vertical = 14.dp, horizontal = 4.dp)
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = if (active) Orange else Color.White, fontWeight = FontWeight.Medium)
    }
}

/** 时间轴缩放档位 */
private val SCALES = floatArrayOf(1f, 2f, 4f, 8f, 16f)

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoEditorScreen(navController: NavHostController, projectId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screenWDp = LocalConfiguration.current.screenWidthDp.toFloat()

    // 片段编辑状态（每个视频素材对应一段）
    val segments = remember { mutableStateListOf<Segment>() }
    // 整体字幕轨：跨所有片段的全局时间轴，start/end 为整个视频的绝对秒数
    val globalSubs = remember { mutableStateListOf<Subtitle>() }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var savingAlbum by remember { mutableStateOf(false) }
    // 保存到相册：先下载结果视频，再写入媒体库（DCIM/Movies），使其在系统相册可见
    fun doSaveVideo() {
        val url = resultUrl ?: return
        scope.launch {
            savingAlbum = true
            val err = saveVideoToAlbum(context, url)
            savingAlbum = false
            Toast.makeText(context, err ?: "已保存到相册", Toast.LENGTH_LONG).show()
        }
    }
    val savePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) doSaveVideo() else Toast.makeText(context, "需要存储权限才能保存到相册", Toast.LENGTH_LONG).show()
    }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    // 字幕长按菜单目标 & 正在编辑的字幕
    var subMenuTarget by remember { mutableStateOf<Subtitle?>(null) }
    var editingSub by remember { mutableStateOf<Subtitle?>(null) }
    var showSpeed by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }

    // 时间轴缩放 + 字幕重叠警告
    var timelineScale by remember { mutableFloatStateOf(1f) }
    var overlapWarn by remember { mutableStateOf(false) }
    var pendingSubtitle by remember { mutableStateOf<Triple<Double, Double?, String>?>(null) }

    // 片段首帧缩略图缓存（idx -> 首帧位图），异步取远程视频第一帧，避免重复取
    val thumbnails = remember { mutableStateMapOf<Int, ImageBitmap>() }
    // 全局胶片条缩略图缓存（全局秒 -> 缩略图），每秒一格
    val filmThumbs = remember { mutableStateMapOf<Double, ImageBitmap>() }
    // 片段首帧是否已尝试加载完成（无论成功/失败），用于停止转圈占位
    val loadedFlags = remember { mutableStateMapOf<Int, Boolean>() }
    // 跨段点击定位时的待 seek 全局时间
    var pendingSeekGlobal by remember { mutableStateOf<Double?>(null) }
    // 拖动进度条时的临时显示位置（scrubbing 用，避免拖动时手柄乱跳）
    var scrubbing by remember { mutableStateOf(false) }
    var scrubGPos by remember { mutableStateOf(0.0) }

    // 全部缩略图/胶片条是否渲染完成：所有片段的 loadedFlags 均为 true（取帧成功或失败都算"完成"）。
    // 未全部完成前视频禁止播放，并展示"正在渲染中"。
    val thumbsReady = remember {
        derivedStateOf {
            segments.isNotEmpty() && segments.indices.all { loadedFlags[it] == true }
        }
    }
    val rendering = !thumbsReady.value
    // 是否已执行过"渲染完成后的首次加载"：仅首次加载后暂停等待用户点击播放，
    // 之后的片段切换/旋转/调速 reload 维持原有播放状态（保留跨段自动续播）。
    var autoStarted by remember { mutableStateOf(false) }

    val engine = remember { PreviewEngine(context) }
    DisposableEffect(Unit) { onDispose { engine.release() } }

    // 加载项目视频素材 → 构建初始片段
    LaunchedEffect(Unit) {
        try {
            val resp = ApiClient.apiService.getPublishMaterials(projectId)
            val vids = resp.data?.videoGroups?.mapNotNull { it.video } ?: emptyList()
            segments.clear()
            segments.addAll(vids.map { m ->
                Segment(
                    assetId = m.id.toString(),
                    src = m.fileUrl,
                    trim = Trim(0.0, m.duration?.toDouble()),
                    volume = 1.0,
                    filterPreset = "none"
                )
            })
            if (segments.isEmpty()) error = "该项目没有视频素材，无法编辑"
        } catch (e: Exception) {
            error = "加载素材失败: ${e.message}"
        } finally {
            loading = false
        }
    }

    // 全局时间轴：每段段内时长（含调速）与全局起点
    val segDurations = segments.map { s ->
        val t = s.trim
        val sp = s.speed ?: 1.0
        if (t != null && t.end != null) ((t.end - t.start) / sp).coerceAtLeast(0.001) else 0.001
    }
    val segStarts = run {
        val list = mutableListOf(0.0)
        repeat(segDurations.size) { i -> list.add(list.last() + segDurations[i]) }
        list
    }
    val totalDuration = (segStarts.lastOrNull() ?: 0.0).coerceAtLeast(0.001)

    // 片段首帧 + 胶片条 + 真实时长：先下载到本地（带鉴权）再取帧/读时长，一次性解决
    // 远程网关 URL 无法被 MediaMetadataRetriever 直接鉴权（曾导致转圈/黑屏/时长 0.0s）。
    LaunchedEffect(segments.size) {
        if (segments.isEmpty()) return@LaunchedEffect
        var acc = 0.0 // 逐段累加真实时长，计算每段全局起点
        segments.forEachIndexed { i, s ->
            val url = mapImageUrl(s.src ?: "")
            val fallback = s.trim?.end ?: 0.001
            if (url.isBlank()) { acc += fallback; loadedFlags[i] = true; return@forEachIndexed }
            val local = downloadVideoForThumbs(context, url)
            if (local == null) { acc += fallback; loadedFlags[i] = true; return@forEachIndexed }
            val sp = s.speed ?: 1.0
            val tStart = s.trim?.start ?: 0.0
            val media = extractSegmentMedia(local, tStart, sp, acc)
            if (media == null) { acc += fallback; loadedFlags[i] = true; return@forEachIndexed }
            val gStart = acc
            acc += media.durationSec
            // 回主线程写 Compose 状态，避免并发改状态
            withContext(Dispatchers.Main) {
                if (media.firstFrame != null) thumbnails[i] = media.firstFrame
                filmThumbs.putAll(media.filmFrames)
                val cur = segments[i]
                if (cur.trim?.end == null) {
                    segments[i] = cur.copy(trim = (cur.trim ?: Trim(0.0, null)).copy(end = media.durationSec))
                }
                loadedFlags[i] = true
            }
        }
    }

    // 预览位置轮询（用于字幕/叠加层显隐 + 进度条）
    var positionMs by remember { mutableStateOf(0L) }
    // 预览位置轮询（进度条/字幕显隐）+ 当前段媒体播到尾自动切下一段续播
    LaunchedEffect(Unit) {
        while (true) {
            delay(150)
            val cur = engine.player.currentPosition
            positionMs = cur
            val dur = engine.player.duration
            // 当前片段媒体播放到末尾（差 300ms 内）→ 自动切到下一个片段继续播放；
            // selectedIndex 变化会触发已有的 LaunchedEffect(selectedIndex, ...) 用下一段 engine.load 并从段起点续播。
            if (selectedIndex < segments.lastIndex && dur > 0 && cur >= dur - 300) {
                selectedIndex += 1
            }
        }
    }

    val sel = if (segments.isEmpty()) null else segments[selectedIndex]

    // 选中片段 / 旋转 / 镜像 / 滤镜 / 裁剪起点 变化 → 重新构建预览（含重新 prepare，旋转才会重绘）
    // ⚠️ 门禁：缩略图/胶片条未全部渲染完成（rendering）前不加载、不播放预览，避免视频在渲染中抢跑。
    LaunchedEffect(
        selectedIndex,
        sel?.rotate,
        sel?.mirror,
        sel?.filterPreset,
        sel?.trim?.start,
        thumbsReady.value
    ) {
        if (!thumbsReady.value) return@LaunchedEffect
        val s = sel ?: return@LaunchedEffect
        val url = mapImageUrl(s.src ?: "")
        engine.load(url, s.rotate, s.mirror, FilterPresetCode.from(s.filterPreset), s.speed, s.trim?.start ?: 0.0)
        // 跨段点击定位：load 完成后跳到目标段内位置
        pendingSeekGlobal?.let { g ->
            val local = g - segStarts[selectedIndex]
            val sp = s.speed ?: 1.0
            val tStart = s.trim?.start ?: 0.0
            engine.seekTo(local * sp + tStart)
            pendingSeekGlobal = null
        }
        // 渲染完成后的首次加载：加载预览但先暂停，显示首帧 + 播放按钮（避免自动播放与"点击播放"预期冲突）；
        // 之后的 reload（片段切换/旋转/调速）维持原有播放状态。
        if (!autoStarted) {
            engine.player.playWhenReady = false
            isPlaying = false
            autoStarted = true
        } else {
            isPlaying = engine.player.playWhenReady
        }
    }

    // 调速走独立实时通道（拖动滑块不重新 prepare，无卡顿）
    LaunchedEffect(sel?.speed) {
        val s = sel ?: return@LaunchedEffect
        engine.setSpeed(s.speed)
    }

    val trimStart = sel?.trim?.start ?: 0.0
    val segPos = (positionMs / 1000.0) - trimStart        // 段内位置（秒）
    val globalPos = if (sel == null) 0.0 else segStarts[selectedIndex] + segPos   // 全局位置
    val globalFrac = (globalPos / totalDuration).coerceIn(0.0, 1.0)
    // 拖动时以 scrubGPos 显示，否则用真实播放位置
    val displayPos = if (scrubbing) scrubGPos else globalPos
    val displayFrac = (displayPos / totalDuration).coerceIn(0.0, 1.0)

    // 当前激活字幕（叠加在画面上，按整体时间轴判断）
    val activeSub = globalSubs.firstOrNull { sub ->
        val end = sub.end ?: Double.MAX_VALUE
        globalPos in sub.start..end
    }

    // 全局 px/秒（随缩放变化），胶片条/字幕轨/进度条共用对齐
    // 注意：Compose 单尺寸上限约 2^18-1 px；当素材 duration 为空时 totalDuration 被 coerce 到 0.001，
    // 直接让 pxPerSec 暴涨（360/0.001=360000 dp → 高密设备 540000 px）触发 Constraints 崩溃。
    // 这里用安全分母并夹上限，胶片条格子另用 weight(1f) 定宽（见下方），双重保险。
    val timelineW = (screenWDp * timelineScale).dp
    val safeTotal = kotlin.math.max(totalDuration, 1.0)
    val pxPerSec = (timelineW.value / safeTotal.toFloat()).coerceAtMost(2000f)

    // 全局秒 -> 最近缩略图（胶片条每格取图）
    fun thumbForGlobalSec(gsec: Double): ImageBitmap? {
        var best: ImageBitmap? = null
        var bestD = Double.MAX_VALUE
        for ((k, v) in filmThumbs) {
            val d = kotlin.math.abs(k - gsec)
            if (d < bestD) { bestD = d; best = v }
        }
        return best
    }

    // 提交生成
    val submitEdit: () -> Unit = {
        if (!submitting) {
            submitting = true
            scope.launch {
                try {
                    val instr = EditInstruction(
                        timeline = Timeline(
                            segments = segments.toList(),
                            transitions = listOf(Transition("fade", 0.5)),
                            subtitles = if (globalSubs.isEmpty()) null else globalSubs.toList()
                        ),
                        audio = Audio(originalVolume = 1.0),
                        output = Output("9:16")
                    )
                    val r = ApiClient.apiService.editVideo(VideoEditReq(projectId, instr))
                    if (r.code != 200 || r.data == null) {
                        val msg = "提交失败：${r.msg ?: "未返回任务ID（后端无数据）"}"
                        error = msg
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        submitting = false
                        return@launch
                    }
                    val taskId = r.data.taskId
                    if (taskId == null) {
                        val msg = "提交失败：${r.msg ?: "未返回任务ID"}"
                        error = msg
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        submitting = false
                        return@launch
                    }
                    repeat(90) {
                        delay(2000)
                        val t = ApiClient.apiService.getVideoEditTask(taskId)
                        when (t.data?.status) {
                            "COMPLETED" -> {
                                resultUrl = t.data.resultUrl
                                showResult = true
                                Toast.makeText(context, "视频已生成，可预览或保存到相册", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            "FAILED" -> {
                                val msg = "渲染失败: ${t.data.errorMessage}"
                                error = msg
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                return@launch
                            }
                        }
                    }
                    if (resultUrl == null && error == null) {
                        val msg = "渲染超时，请稍后在历史中查看"
                        error = msg
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    val msg = "提交失败: ${e.message}"
                    error = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                } finally {
                    submitting = false
                }
            }
        }
    }

    // 真正写入字幕（无重叠或用户确认后调用）——写入整体字幕轨，时间为整体时间轴绝对秒数
    fun commitSubtitle(start: Double, end: Double?, text: String) {
        globalSubs.add(Subtitle(start = start, end = end, text = text))
        showSubtitleDialog = false
        Toast.makeText(context, "已添加字幕", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视频编辑", fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("‹", fontSize = 24.sp, color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Orange)
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Orange)
            }
        } else if (error != null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = Color.Red, fontSize = 14.sp)
            }
        } else {
            // 固定布局、整屏不滚动：预览占满上方剩余空间，工具区紧跟，生成栏贴底（无大块空白）
            Column(Modifier.fillMaxSize().padding(padding)) {
                // ============ 上区：预览占满，左右命令栏 ============
                Row(
                    Modifier.fillMaxWidth().weight(1f).background(Color.Black),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧栏：旋转 / 镜像（76dp 宽，按钮大）
                    Column(
                        Modifier.width(76.dp).fillMaxHeight().padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        sel?.let { s ->
                            RailItem("🔄", "旋转") {
                                val cur = segments[selectedIndex]
                                segments[selectedIndex] = cur.copy(rotate = (cur.rotate + 90) % 360)
                            }
                            Spacer(Modifier.height(16.dp))
                            RailItem("🪞", "镜像", active = s.mirror) {
                                val cur = segments[selectedIndex]
                                segments[selectedIndex] = cur.copy(mirror = !cur.mirror)
                            }
                        }
                    }
                    // 视频预览（9:16 letterbox）
                    Box(
                        Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.fillMaxHeight().aspectRatio(9f / 16f)) {
                            AndroidView(
                                factory = { ctx -> PlayerView(ctx).apply { useController = false; player = engine.player } },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // 播放 / 暂停（渲染中禁用，避免视频抢跑）
                        IconButton(
                            onClick = { isPlaying = engine.togglePlayback() },
                            enabled = !rendering,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(64.dp)
                                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                                    .padding(10.dp)
                            )
                        }
                        // 字幕叠加
                        activeSub?.let { sub ->
                            val align = when (sub.align) {
                                "left" -> Alignment.BottomStart
                                "right" -> Alignment.BottomEnd
                                else -> Alignment.BottomCenter
                            }
                            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = align) {
                                Text(
                                    sub.text,
                                    color = Color.White,
                                    fontSize = (sub.size ?: 30).sp,
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f)).padding(4.dp)
                                )
                            }
                        }
                        // 渲染中遮罩：缩略图/胶片条未全部渲染完成前覆盖预览区，视频禁止播放
                        if (rendering) {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                        .padding(24.dp)
                                ) {
                                    CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp, color = Color.White)
                                    Spacer(Modifier.height(12.dp))
                                    Text("正在渲染中…", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    // 右侧栏：字幕 / 调速（76dp 宽，按钮大）
                    Column(
                        Modifier.width(76.dp).fillMaxHeight().padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        sel?.let { s ->
                            RailItem("🅰", "字幕") { showSubtitleDialog = true }
                            Spacer(Modifier.height(16.dp))
                            RailItem("⏩", "调速", active = showSpeed) { showSpeed = !showSpeed }
                        }
                    }
                }

                // ============ 全局进度胶片条 + 进度条 + 字幕轨道（视频正下方）============
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                        .background(Color(0xFFF7F7F8), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    // 顶行：全局时间文案 + 缩放
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${fmt(displayPos)}s / ${fmt(totalDuration)}s", fontSize = 12.sp, color = Gray700, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Text("总进度", fontSize = 12.sp, color = Gray500)
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val idx = SCALES.indexOfFirst { it >= timelineScale }
                                timelineScale = SCALES[(if (idx <= 0) 0 else idx - 1).coerceAtLeast(0)]
                            },
                            modifier = Modifier.size(30.dp)
                        ) { Text("－", fontSize = 18.sp, color = Orange, fontWeight = FontWeight.Bold) }
                        Text("${timelineScale.toInt()}x", fontSize = 12.sp, color = Orange, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                        IconButton(
                            onClick = {
                                val idx = SCALES.indexOfFirst { it > timelineScale }
                                timelineScale = SCALES.getOrElse(idx) { SCALES.last() }
                            },
                            modifier = Modifier.size(30.dp)
                        ) { Text("＋", fontSize = 18.sp, color = Orange, fontWeight = FontWeight.Bold) }
                    }

                    Spacer(Modifier.height(6.dp))

                    // 横滑容器：仅胶片条 + 字幕轨共用 timelineW（缩放时横向滚动查看）
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Column(Modifier.width(timelineW)) {
                            // —— 胶片条（每秒一格缩略图 + 段边界 + 播放头）——
                            Box(Modifier.fillMaxWidth().height(54.dp).background(Color(0xFF1A1A1A), RoundedCornerShape(6.dp))) {
                                Row(Modifier.fillMaxSize()) {
                                    val totalSecs = totalDuration.toInt() + 1
                                    repeat(totalSecs) { sec ->
                                        val gsec = sec.toDouble()
                                        val tb = thumbForGlobalSec(gsec)
                                        val isBoundary = segStarts.drop(1).any { kotlin.math.abs(it - gsec) < 0.5 }
                                        Box(
                                            Modifier.weight(1f).fillMaxHeight()
                                                .background(Color.Black)
                                                .padding(end = 1.dp)
                                        ) {
                                            if (tb != null) {
                                                Image(bitmap = tb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            }
                                            if (isBoundary) {
                                                Box(Modifier.align(Alignment.CenterStart).width(2.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.9f)))
                                            }
                                        }
                                    }
                                }
                                // 播放头（贯穿胶片条）
                                Box(Modifier.offset(x = (displayFrac * timelineW.value).dp - 1.dp).width(2.dp).fillMaxHeight().background(Color.White))
                            }
                            Spacer(Modifier.height(6.dp))
                            // —— 整体字幕轨道（全局时间轴，绝对秒数定位）——
                            Box(Modifier.fillMaxWidth().height(36.dp)) {
                                globalSubs.forEach { sub ->
                                    val effEnd = sub.end ?: (sub.start + 3.0)
                                    val x = (sub.start * pxPerSec).dp
                                    val w = ((effEnd - sub.start) * pxPerSec).dp
                                    Box(
                                        Modifier.offset(x = x).width(w).height(32.dp)
                                            .background(SubColor, RoundedCornerShape(4.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    // 跨段跳转：定位到该字幕所在的片段并从段内起点续播
                                                    val i = segStarts.indexOfLast { it <= sub.start }.coerceIn(0, segments.size - 1)
                                                    if (i != selectedIndex) {
                                                        selectedIndex = i
                                                        pendingSeekGlobal = sub.start
                                                    } else {
                                                        val tStart = segments[i].trim?.start ?: 0.0
                                                        engine.seekTo(sub.start - segStarts[i] + tStart)
                                                    }
                                                },
                                                onLongClick = { subMenuTarget = sub }
                                            )
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(sub.text.ifBlank { "(空字幕)" }, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${fmt(sub.start)}-${sub.end?.let { fmt(it) } ?: "∞"}s", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
                                        }
                                    }
                                }
                                if (globalSubs.isEmpty()) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                        Text("暂无字幕，点右侧「字幕」添加", fontSize = 11.sp, color = Gray500)
                                    }
                                }
                            }
                            // 字幕长按菜单（编辑 / 删除）
                            DropdownMenu(
                                expanded = subMenuTarget != null,
                                onDismissRequest = { subMenuTarget = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("编辑") },
                                    onClick = {
                                        editingSub = subMenuTarget
                                        subMenuTarget = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除", color = Color(0xFFD32F2F)) },
                                    onClick = {
                                        subMenuTarget?.let { globalSubs.remove(it) }
                                        subMenuTarget = null
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // —— 时间标尺 + 全局进度条（脱离横滑容器，确保可拖动；满宽映射 0..totalDuration）——
                    ScrubberBar(
                        totalDuration = totalDuration,
                        displayFrac = displayFrac,
                        enabled = !rendering,
                        segBoundaries = segStarts.drop(1),
                        onScrub = { gT ->
                            scrubbing = true
                            scrubGPos = gT
                            val i = segStarts.indexOfLast { it <= gT }.coerceIn(0, segments.size - 1)
                            if (i == selectedIndex) {
                                val localSec = gT - segStarts[i]
                                val sp = segments[i].speed ?: 1.0
                                val tStart = segments[i].trim?.start ?: 0.0
                                engine.seekTo(localSec * sp + tStart)
                            }
                        },
                        onScrubEnd = { gT ->
                            scrubbing = false
                            val i = segStarts.indexOfLast { it <= gT }.coerceIn(0, segments.size - 1)
                            if (i == selectedIndex) {
                                val localSec = gT - segStarts[i]
                                val sp = segments[i].speed ?: 1.0
                                val tStart = segments[i].trim?.start ?: 0.0
                                engine.seekTo(localSec * sp + tStart)
                            } else {
                                selectedIndex = i
                                pendingSeekGlobal = gT
                            }
                        }
                    )
                }

                // ============ 调速滑块（点右侧"调速"展开，固定高度）============
                if (showSpeed && sel != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("调速", fontSize = 13.sp, color = Gray700, modifier = Modifier.width(48.dp))
                        Slider(
                            value = sel.speed.toFloat(),
                            onValueChange = {
                                val cur = segments[selectedIndex]
                                segments[selectedIndex] = cur.copy(speed = it.toDouble())
                            },
                            valueRange = 0.5f..2f,
                            steps = 14,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${"%.1f".format(sel.speed)}x",
                            fontSize = 13.sp, color = Orange, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(44.dp), textAlign = TextAlign.End
                        )
                    }
                }

                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

                // ============ 片段时间轴（固定高度）============
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("片段", fontSize = 13.sp, color = Gray700, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("点选片段后左右按钮编辑", fontSize = 11.sp, color = Gray500)
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(segments.size) { idx ->
                        val s = segments[idx]
                        val thumb = thumbnails[idx]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(4.dp)
                                .clickable { selectedIndex = idx }
                                .background(
                                    if (idx == selectedIndex) Orange.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(6.dp)
                        ) {
                            // 首帧缩略图（取帧中显示转圈占位）
                            Box(
                                Modifier.size(40.dp).background(Color.Black, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (!loadedFlags.contains(idx)) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Gray500)
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text("片段${idx + 1}", fontSize = 11.sp, color = if (idx == selectedIndex) Orange else Gray700, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(FilterPresetCode.from(s.filterPreset).label, fontSize = 10.sp, color = Gray500, maxLines = 1)
                        }
                    }
                }

                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

                // ============ 滤镜（固定高度）============
                Text(
                    "滤镜",
                    Modifier.padding(start = 12.dp, top = 6.dp, bottom = 4.dp),
                    fontSize = 13.sp, color = Gray700, fontWeight = FontWeight.Bold
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(FILTER_PRESET_LIST) { fp ->
                        val selected = sel?.filterPreset == fp.code
                        Box(
                            modifier = Modifier.padding(4.dp)
                                .clickable { if (sel != null) segments[selectedIndex] = sel.copy(filterPreset = fp.code) }
                                .background(
                                    if (selected) Orange else Color.LightGray.copy(alpha = 0.4f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(fp.label, fontSize = 13.sp, color = if (selected) Color.White else Gray700, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // ============ 底部操作栏（贴底，固定高度）============
                Surface(color = Orange, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (resultUrl != null) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("已生成", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = { showResult = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White))
                            ) {
                                Text("预览", fontSize = 13.sp)
                            }
                            Spacer(Modifier.weight(1f))
                        } else {
                            Text(
                                "实时预览编辑效果，生成后可直接发布",
                                color = Color.White, fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = submitEdit,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            enabled = !submitting,
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            if (submitting) CircularProgressIndicator(
                                modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Orange
                            )
                            else Text("生成视频", color = Orange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // 生成结果预览（播放结果视频 + 保存到相册）
    if (showResult && resultUrl != null) {
        ResultPreviewDialog(
            url = resultUrl!!,
            saving = savingAlbum,
            onSave = {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                ) {
                    savePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    doSaveVideo()
                }
            },
            onDismiss = { showResult = false }
        )
    }

    // 添加字幕弹窗（整体字幕轨，无需选中片段）
    if (showSubtitleDialog) {
        var text by remember { mutableStateOf("") }
        var start by remember { mutableStateOf(globalPos.toString()) }
        var end by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false },
            title = { Text("添加字幕", fontSize = 15.sp) },
            text = {
                Column {
                    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("字幕内容", fontSize = 12.sp) })
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("开始秒", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("结束秒(可空)", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("整体时间轴秒数（0 = 视频开头）。留空结束秒表示到视频末尾。", fontSize = 11.sp, color = Gray500)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newStart = start.toDoubleOrNull() ?: 0.0
                    val newEndRaw = end.toDoubleOrNull()
                    val newEnd = newEndRaw ?: (newStart + 3.0)
                    // 重叠检测：与整体字幕轨已有字幕求时间交集
                    val overlaps = globalSubs.mapIndexedNotNull { i, ex ->
                        val exEnd = ex.end ?: Double.MAX_VALUE
                        if (newStart < exEnd && newEnd > ex.start) (i + 1) else null
                    }
                    if (overlaps.isNotEmpty()) {
                        // 先暂存，弹警告框让用户确认是否仍添加
                        pendingSubtitle = Triple(newStart, newEndRaw, text)
                        overlapWarn = true
                    } else {
                        commitSubtitle(newStart, newEndRaw, text)
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showSubtitleDialog = false }) { Text("取消") } }
        )
    }

    // 字幕时间重叠警告
    if (overlapWarn && pendingSubtitle != null) {
        val (ps, pe, pt) = pendingSubtitle!!
        val overlapList = globalSubs.mapIndexedNotNull { i, ex ->
            val exEnd = ex.end ?: Double.MAX_VALUE
            if (ps < exEnd && (pe ?: Double.MAX_VALUE) > ex.start) (i + 1) else null
        }
        AlertDialog(
            onDismissRequest = { overlapWarn = false },
            title = { Text("字幕时间重叠", fontSize = 15.sp, color = Color(0xFFD32F2F)) },
            text = {
                Text(
                    "该字幕（${fmt(ps)}-${pe?.let { fmt(it) } ?: "∞"}s）与第 ${overlapList.joinToString("、")} 条字幕时间有覆盖，\n重叠会导致播放时文字互相打架。仍要添加吗？",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    commitSubtitle(ps, pe, pt)
                    overlapWarn = false
                    pendingSubtitle = null
                }) { Text("仍要添加", color = Color(0xFFD32F2F)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    overlapWarn = false
                    pendingSubtitle = null
                    showSubtitleDialog = true
                }) { Text("返回修改") }
            }
        )
    }

    // 编辑字幕弹窗（整体字幕轨，保留原样式字段）
    if (editingSub != null) {
        val s0 = editingSub!!
        var text by remember(editingSub) { mutableStateOf(s0.text) }
        var start by remember(editingSub) { mutableStateOf(s0.start.toString()) }
        var end by remember(editingSub) { mutableStateOf(s0.end?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { editingSub = null },
            title = { Text("编辑字幕", fontSize = 15.sp) },
            text = {
                Column {
                    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("字幕内容", fontSize = 12.sp) })
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("开始秒", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("结束秒(可空)", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("整体时间轴秒数（0 = 视频开头）。留空结束秒表示到视频末尾。", fontSize = 11.sp, color = Gray500)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ns = start.toDoubleOrNull() ?: 0.0
                    val neRaw = end.toDoubleOrNull()
                    val ne = neRaw ?: (ns + 3.0)
                    val idx = globalSubs.indexOf(s0)
                    if (idx >= 0) {
                        globalSubs[idx] = Subtitle(
                            start = ns, end = ne, text = text,
                            size = s0.size, color = s0.color, position = s0.position, align = s0.align
                        )
                    }
                    editingSub = null
                    Toast.makeText(context, "已更新字幕", Toast.LENGTH_SHORT).show()
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { editingSub = null }) { Text("取消") } }
        )
    }
}

/**
 * 生成结果预览弹窗：用带鉴权的独立 ExoPlayer 播放结果视频（与编辑预览 engine 解耦，互不影响），
 * 并提供「保存到相册」入口。关闭时释放播放器，避免内存泄漏。
 */
@OptIn(UnstableApi::class)
@Composable
private fun ResultPreviewDialog(
    url: String,
    saving: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resultPlayer = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("TaskPlatform/Android")
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${ApiClient.token}"))
        val msf = DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory)
        ExoPlayer.Builder(context).setMediaSourceFactory(msf).build().apply {
            playWhenReady = true
            setMediaItem(MediaItem.fromUri(mapImageUrl(url)))
            prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { resultPlayer.release() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF101010),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("视频已生成", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                AndroidView(
                    modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).background(Color.Black),
                    factory = { PlayerView(it).apply { useController = true; player = resultPlayer } }
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text("关闭") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onSave,
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange)
                    ) {
                        if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("保存到相册", color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * 下载结果视频（带鉴权）并写入媒体库，使其在系统相册（DCIM/Movies）可见。
 * @return null 表示成功，否则返回错误文案。
 */
private suspend fun saveVideoToAlbum(context: Context, url: String): String? = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val reqBuilder = Request.Builder().url(mapImageUrl(url))
        if (ApiClient.token.isNotEmpty()) reqBuilder.addHeader("Authorization", "Bearer ${ApiClient.token}")
        val resp = client.newCall(reqBuilder.build()).execute()
        if (!resp.isSuccessful) return@withContext "下载失败(${resp.code})"
        val body = resp.body ?: return@withContext "下载内容为空"
        val bytes = body.bytes()

        // 先落地到应用缓存，再拷贝进媒体库（避免直接对流写入失败时残留）
        val cacheFile = File(context.cacheDir, "generated/video_${System.currentTimeMillis()}.mp4")
        cacheFile.parentFile?.mkdirs()
        FileOutputStream(cacheFile).use { it.write(bytes) }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "task_video_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/TaskPlatform")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: return@withContext "无法创建媒体库条目"
        resolver.openOutputStream(uri)?.use { os ->
            FileInputStream(cacheFile).use { fis -> fis.copyTo(os) }
        } ?: return@withContext "无法写入媒体库"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        cacheFile.delete()
        null
    } catch (e: Exception) {
        e.message
    }
}

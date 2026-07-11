package com.task.platform.ui.publish

import com.task.platform.rewriteLocalImageUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.content.Context
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.task.platform.model.MergeHistoryVO
import com.task.platform.model.PublishMaterialDTO
import com.task.platform.model.PublishTaskDTO
import com.task.platform.viewmodel.PublishViewModel
import com.task.platform.viewmodel.PublishViewModel.MergeState
import java.io.IOException

// ─── 配色体系 ───────────────────────────────────────
private val Orange = Color(0xFFFF8C00)
private val OrangeLight = Color(0xFFFFB347)
private val OrangeBg = Color(0xFFFFF8F0)
private val Gray50 = Color(0xFFFAFAFA)
private val Gray100 = Color(0xFFF5F5F5)
private val Gray300 = Color(0xFFE0E0E0)
private val Gray400 = Color(0xFFBDBDBD)
private val Gray500 = Color(0xFF9E9E9E)
private val Gray700 = Color(0xFF616161)
private val Gray900 = Color(0xFF212121)

// 平台标签颜色
private val DouyinPink = Color(0xFFE91E63)
private val XiaohongshuRed = Color(0xFFFF2442)

// 状态标签颜色
private val StatusPending = Color(0xFFFF8C00)   // 橙色
private val StatusClaimed = Color(0xFF2196F3)   // 蓝色
private val StatusCompleted = Color(0xFF4CAF50) // 绿色

/**
 * 发布任务大厅 — 类似于 TaskHallScreen 的设计风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen() {
    val viewModel: PublishViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val taskList by viewModel.taskList.collectAsState()
    val myTaskList by viewModel.myTaskList.collectAsState()
    val actionError by viewModel.actionError.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    // 弹窗状态
    var showClaimDialog by remember { mutableStateOf(false) }
    var claimTargetId by remember { mutableStateOf<Long?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var detailTask by remember { mutableStateOf<PublishTaskDTO?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadTasks()
    }

    // ── 下拉刷新 ──
    val pullRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            if (selectedTab == 0) viewModel.loadTasks() else viewModel.loadMyTasks()
            pullRefreshState.endRefresh()
        }
    }

    // 错误弹窗
    LaunchedEffect(actionError) {
        actionError ?: return@LaunchedEffect
    }

    // ===== 页面切换：详情页 =====
    if (showDetailDialog && detailTask != null) {
        PublishDetailScreen(
            task = detailTask!!,
            onBack = {
                showDetailDialog = false
                detailTask = null
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray50)
    ) {
        // ===== 顶部渐变头部 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Orange, OrangeLight)
                    ),
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column {
                Text(
                    text = "发布任务",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "领取发布任务，赚取奖励",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 统计行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val pendingCount = taskList.count { it.status == "pending" }
                    val myTaskCount = taskList.count { it.status == "claimed" || it.status == "submitted" || it.status == "rejected" || it.status == "completed" }
                    HallStatItem("全部任务", "${taskList.size}", Icons.Default.Assignment)
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                    HallStatItem(
                        "待领取",
                        "$pendingCount",
                        Icons.Default.Pending
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                    HallStatItem(
                        "我的任务",
                        "$myTaskCount",
                        Icons.Default.CheckCircle
                    )
                }
            }
        }

        // ===== 顶部Tab栏 =====
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = Orange,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                    viewModel.loadTasks()
                },
                text = {
                    Text(
                        text = "全部任务",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp
                    )
                },
                selectedContentColor = Orange,
                unselectedContentColor = Gray500
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    viewModel.loadMyTasks()
                },
                text = {
                    Text(
                        text = "我的任务",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp
                    )
                },
                selectedContentColor = Orange,
                unselectedContentColor = Gray500
            )
        }

        // ===== 内容区 =====
        when (uiState) {
            is PublishViewModel.UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Orange)
                }
            }
            is PublishViewModel.UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Gray300
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = (uiState as PublishViewModel.UiState.Error).message,
                            color = Gray500,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = {
                            viewModel.resetError()
                            if (selectedTab == 0) viewModel.loadTasks() else viewModel.loadMyTasks()
                        }) {
                            Text("重试", color = Orange)
                        }
                    }
                }
            }
            else -> {
                val currentList = if (selectedTab == 0) taskList else myTaskList
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullRefreshState.nestedScrollConnection)
                ) {
                    if (currentList.isEmpty()) {
                        PublishEmptyView()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(currentList, key = { it.id }) { task ->
                                PublishTaskCard(
                                    task = task,
                                    isMyTask = selectedTab == 1,
                                    onClaim = {
                                        claimTargetId = task.id
                                        showClaimDialog = true
                                    },
                                    onComplete = {
                                        viewModel.completeTask(task.id) {
                                            viewModel.loadMyTasks()
                                        }
                                    },
                                    onDetail = {
                                        detailTask = task
                                        showDetailDialog = true
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                    PullToRefreshContainer(
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }

    // ===== 领取确认弹窗 =====
    if (showClaimDialog && claimTargetId != null) {
        AlertDialog(
            onDismissRequest = {
                showClaimDialog = false
                claimTargetId = null
            },
            title = {
                Text("确认领取", fontWeight = FontWeight.Bold, color = Gray900)
            },
            text = {
                Text(
                    "确认领取该发布任务？领取后请在规定时间内完成。",
                    color = Gray700,
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val taskId = claimTargetId ?: return@TextButton
                        viewModel.claimTask(taskId) {
                            showClaimDialog = false
                            claimTargetId = null
                            viewModel.loadTasks()
                            viewModel.loadMyTasks()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Orange)
                ) {
                    Text("确认领取", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showClaimDialog = false
                    claimTargetId = null
                }) {
                    Text("取消", color = Gray500)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 操作错误 Snackbar
    if (actionError != null) {
        LaunchedEffect(actionError) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearActionError()
        }
    }
}

// ==================== 统计项 ====================

@Composable
private fun HallStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

// ==================== 发布任务卡片 ====================

@Composable
private fun PublishTaskCard(
    task: PublishTaskDTO,
    isMyTask: Boolean,
    onClaim: () -> Unit,
    onComplete: () -> Unit,
    onDetail: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDetail() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 顶部：项目名 + 状态标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧图标
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OrangeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = Orange,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // 中间：项目名
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.projectName.ifBlank { "未命名项目" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Gray900,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTime(task.scheduledAt ?: task.createdAt),
                        fontSize = 13.sp,
                        color = Gray500,
                        maxLines = 1
                    )
                }

                // 右侧：状态标签
                PublishStatusTag(status = task.submissionStatus?.takeIf { it.isNotBlank() } ?: task.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 配额信息：剩余 / 总
            val remainCount = task.totalQuota - task.usedQuota
            if (remainCount >= 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "剩余 $remainCount / ${task.totalQuota}",
                        fontSize = 11.sp,
                        color = if (remainCount > 0) Gray500 else Color(0xFFE53935)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 底部：平台标签 + 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 平台标签
                val platforms = parsePlatforms(task.platforms)
                platforms.forEach { platform ->
                    PlatformTag(platform = platform)
                }

                Spacer(modifier = Modifier.weight(1f))

                // 操作按钮
                if (!isMyTask && task.status == "pending") {
                    // 全部任务中的待领取 → 显示领取按钮
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Orange,
                        modifier = Modifier.clickable { onClaim() }
                    ) {
                        Text(
                            text = "领取",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (isMyTask && task.status == "claimed") {
                    // 我的任务中已领取 → 显示完成按钮
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = StatusCompleted,
                        modifier = Modifier.clickable { onComplete() }
                    ) {
                        Text(
                            text = "完成",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 我的任务显示查看详情箭头
                if (isMyTask) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "查看详情",
                        tint = Gray300,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ==================== 状态标签 ====================

@Composable
private fun PublishStatusTag(status: String) {
    val s = status.lowercase()
    val (label, color) = when (s) {
        "pending", "online" -> "待领取" to StatusPending
        "claimed", "merged" -> "已领取" to StatusClaimed
        "submitted" -> "审核中" to Orange
        "passed" -> "已奖励" to StatusCompleted
        "rejected" -> "已拒绝（需要重新提交审核）" to Color.Red
        "completed", "running" -> "审核中" to Orange
        "expired", "timeout" -> "已超时" to Gray500
        "cancelled", "offline", "failed" -> "待领取" to Gray500
        else -> "待领取" to Gray500
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

// ==================== 平台标签 ====================

@Composable
private fun PlatformTag(platform: String) {
    val color = when (platform.trim()) {
        "抖音" -> DouyinPink
        "小红书" -> XiaohongshuRed
        else -> Gray500
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = platform.trim(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

// ==================== 空状态 ====================

@Composable
private fun PublishEmptyView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Inbox,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = Gray300
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无发布任务",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Gray500
            )
            Text(
                text = "请稍后再来查看",
                fontSize = 16.sp,
                color = Gray500,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ==================== 详情弹窗（我的任务） ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishDetailScreen(
    task: PublishTaskDTO,
    onBack: () -> Unit
) {
    // 图片预览 Dialog 状态
    var showImagePreview by remember { mutableStateOf(false) }
    var imagePreviewUrl by remember { mutableStateOf("") }

    // 音乐播放器状态
    var currentPlayingUrl by remember { mutableStateOf<String?>(null) }

    // 视频预览状态
    var showVideoPreview by remember { mutableStateOf(false) }
    var videoPreviewUrl by remember { mutableStateOf("") }
    var showHistoryGrid by remember { mutableStateOf(false) }
    var showPublishDialog by remember { mutableStateOf(false) }
    var showSubmitDialog by remember { mutableStateOf(false) }
    var isPublished by remember { mutableStateOf(false) }
    var submittedScreenshots by remember { mutableStateOf<List<String>>(emptyList()) }
    var submissionStatus by remember { mutableStateOf("") }
    var submissionReward by remember { mutableStateOf<Double?>(null) }
    // 剪辑选项
    var transitionType by remember { mutableStateOf("none") }
    var fadeInOut by remember { mutableStateOf(false) }
    var subtitleText by remember { mutableStateOf("") }
    var isClaimed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var selectedMergeUrl by remember { mutableStateOf("") }

    // 进入页面时检查是否已有领取/提交记录（不自动领取）
    LaunchedEffect(task.id) {
        try {
            val statusResp = com.task.platform.network.ApiClient.apiService.getSubmissionStatus(task.id)
            android.util.Log.d("PublishDetail", "submission status: code=${statusResp.code}, data=${statusResp.data}")
            if (statusResp.code == 200 && statusResp.data != null) {
                // 有记录 → 已领取
                isClaimed = true
                val screenshots = statusResp.data["screenshots"] as? String
                submissionStatus = (statusResp.data["status"] as? String) ?: ""
                val rewardObj = statusResp.data["rewardAmount"]
                submissionReward = when (rewardObj) {
                    is Double -> rewardObj
                    is Number -> rewardObj.toDouble()
                    else -> null
                }
                android.util.Log.d("PublishDetail", "submission status=${submissionStatus}, screenshots=$screenshots")
                if (!screenshots.isNullOrBlank()) {
                    submittedScreenshots = screenshots.split(",")
                    selectedMergeUrl = (statusResp.data["mergedVideoUrl"] as? String) ?: ""
                }
            } else {
                // 无记录 → 未领取，isClaimed 保持 false
                android.util.Log.d("PublishDetail", "no record found, task not claimed yet")
            }
        } catch (e: Exception) {
            // 接口不存在或报错 → 未领取
            isClaimed = false
            android.util.Log.d("PublishDetail", "submission check failed, treat as unclaimed: ${e.message}")
        }
    }

    val context = LocalContext.current
    val pmViewModel: PublishViewModel = hiltViewModel()
    val taskVideoList = remember(task.id) {
        mutableStateListOf<VideoSortItem>().also { list ->
            val videos = (task.materials ?: emptyList())
                .filter { it.type == "video" }
                .sortedBy { it.sortOrder }
            list.addAll(videos.map { VideoSortItem(it.id, it.title ?: "第${it.sortOrder + 1}段", it.fileUrl ?: "") })
        }
    }
    // 当 task.materials 为空时，从随机预览的 videoGroups 中补充视频列表
    val materialsPreview by pmViewModel.materialsPreview.collectAsState()
    LaunchedEffect(materialsPreview) {
        if (taskVideoList.isEmpty() && materialsPreview?.videoGroups != null) {
            taskVideoList.clear()
            materialsPreview!!.videoGroups.forEachIndexed { idx, vg ->
                val video = vg.video
                if (video != null) {
                    taskVideoList.add(VideoSortItem(video.id, "第${vg.sortOrder + 1}段", video.fileUrl ?: ""))
                }
            }
        }
    }
    val mediaPlayer = remember { MediaPlayer() }

    // 释放 MediaPlayer 资源
    DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 图片预览 Dialog
    if (showImagePreview && imagePreviewUrl.isNotBlank()) {
        Dialog(onDismissRequest = { showImagePreview = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { showImagePreview = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = mapImageUrl(imagePreviewUrl),
                    contentDescription = "图片预览",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    // 视频预览 Dialog
    if (showVideoPreview && videoPreviewUrl.isNotBlank()) {
        VideoPreviewDialog(
            url = mapImageUrl(videoPreviewUrl),
            onDismiss = { showVideoPreview = false }
        )
    }

    // 合并历史网格
    if (showHistoryGrid) {
        HistoryGridDialog(
            historyList = pmViewModel.mergeHistory.collectAsState().value,
            onSelect = {
                selectedMergeUrl = it.outputUrl
                showHistoryGrid = false
            },
            onPlay = {
                videoPreviewUrl = it.outputUrl
                showVideoPreview = true
            },
            onDismiss = { showHistoryGrid = false }
        )
    }

    val mergeState by pmViewModel.mergeState.collectAsState()

    // 发布确认弹窗
    if (showPublishDialog) {
        val mergedUrl = when (val s = mergeState) {
            is MergeState.Success -> s.url
            else -> selectedMergeUrl
        }
        AlertDialog(
            onDismissRequest = { showPublishDialog = false },
            title = { Text("确认发布", fontWeight = FontWeight.Bold) },
            text = { Text("确认后标记为已发布，并打开分享平台。发布后请在对应平台截图，然后提交审核。") },
            confirmButton = {
                Button(
                    onClick = {
                        showPublishDialog = false
                        coroutineScope.launch {
                            try {
                                com.task.platform.network.ApiClient.apiService.publishPublishTask(task.id)
                                // 标记已发布：本地状态 + 服务端记录状态(MERGED)
                                isPublished = true
                                submissionStatus = "MERGED"
                                // 保存合并URL用于后续提交
                                selectedMergeUrl = mergedUrl ?: selectedMergeUrl
                                openSharePlatform(context, task.platforms)
                            } catch (e: Exception) {
                                Toast.makeText(context, "发布失败：${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) { Text("确认发布", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showPublishDialog = false }) { Text("取消") }
            }
        )
    }

    // 提交审核弹窗
    if (showSubmitDialog) {
        SubmitReviewDialog(
            taskId = task.id,
            mergedVideoUrl = selectedMergeUrl,
            context = context,
            onDismiss = { showSubmitDialog = false },
            onSubmitted = { screenshots ->
                submittedScreenshots = screenshots
                submissionStatus = "SUBMITTED"
                showSubmitDialog = false
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Gray50)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ===== 顶部栏（仿 TaskDetailScreen） =====
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = Gray900)
                    }
                    Text(
                        "发布任务详情",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Gray900,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ===== 可滚动内容 =====
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 项目名
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("项目名称", fontSize = 13.sp, color = Gray500)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            (task.projectName ?: "").ifBlank { "未命名项目" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Gray900
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 发布平台
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("发布平台", fontSize = 13.sp, color = Gray500)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val platforms = parsePlatforms(task.platforms)
                            platforms.forEach { platform ->
                                PlatformTag(platform = platform)
                            }
                        }
                    }
                }

                // 发布文案
                if (!task.publishText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("发布文案", fontSize = 13.sp, color = Gray500)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(task.publishText, fontSize = 14.sp, color = Gray900, lineHeight = 20.sp)
                        }
                    }
                }

                // 素材列表
                if (!task.materials.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("素材列表", fontSize = 13.sp, color = Gray500)
                            Spacer(modifier = Modifier.height(8.dp))
                            task.materials.forEach { material ->
                                MaterialItem(material = material)
                            }
                        }
                    }
                }

                // 随机素材预览（不套 Card，保持独立区块）
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val materialsPreview by pmViewModel.materialsPreview.collectAsState()
                        val refreshCooldown by pmViewModel.refreshCooldownSeconds.collectAsState()
                        val materialsError by pmViewModel.materialsError.collectAsState()

                        LaunchedEffect(task.projectId) {
                            pmViewModel.loadMaterials(task.projectId)
                            pmViewModel.loadMergeHistory(task.projectId)
                        }

                        Text("随机素材预览", fontSize = 13.sp, color = Gray500)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (materialsError != null) {
                            Text(
                                text = materialsError ?: "加载失败",
                                fontSize = 12.sp,
                                color = Color.Red
                            )
                        } else if (materialsPreview == null) {
                            Text(
                                text = "加载中...",
                                fontSize = 12.sp,
                                color = Gray500
                            )
                        } else {
                            val preview = materialsPreview!!

                            // 随机文案
                            preview.textMaterial?.let { tm ->
                                Text("随机文案", fontSize = 12.sp, color = Gray700)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(tm.content ?: "", fontSize = 14.sp, color = Gray900)
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // 随机图片
                            preview.imageMaterial?.let { im ->
                                Text("随机图片", fontSize = 12.sp, color = Gray700)
                                Spacer(modifier = Modifier.height(4.dp))
                                if (!im.fileUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = mapImageUrl(im.fileUrl),
                                        contentDescription = "图片预览",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                imagePreviewUrl = im.fileUrl
                                                showImagePreview = true
                                            },
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text("-", fontSize = 12.sp, color = Gray500)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // 随机音乐
                            preview.musicMaterial?.let { mm ->
                                Text("随机音乐", fontSize = 12.sp, color = Gray700)
                                Spacer(modifier = Modifier.height(4.dp))
                                if (!mm.fileUrl.isNullOrBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                if (currentPlayingUrl == mm.fileUrl && mediaPlayer.isPlaying) {
                                                    mediaPlayer.pause()
                                                    currentPlayingUrl = null
                                                } else {
                                                    try {
                                                        mediaPlayer.reset()
                                                        mediaPlayer.setDataSource(context, Uri.parse(mapImageUrl(mm.fileUrl)))
                                                        mediaPlayer.prepare()
                                                        mediaPlayer.start()
                                                        currentPlayingUrl = mm.fileUrl
                                                    } catch (e: IOException) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                if (currentPlayingUrl == mm.fileUrl && mediaPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "播放/暂停"
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = mm.title ?: "背景音乐",
                                            fontSize = 14.sp,
                                            color = Gray700,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Text("-", fontSize = 12.sp, color = Gray500)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            // 可拖拽排序的视频网格（3列）
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("视频素材排序（长按拖动）", fontSize = 13.sp, color = Gray500)
                            Spacer(modifier = Modifier.height(4.dp))
                            var gridDraggedIndex by remember { mutableIntStateOf(-1) }
                            var gridDragOffsetX by remember { mutableFloatStateOf(0f) }
                            var gridDragOffsetY by remember { mutableFloatStateOf(0f) }
                            val gridRowHeightPx = with(LocalDensity.current) { 120.dp.toPx() }
                            val gridColWidthPx = with(LocalDensity.current) { 108.dp.toPx() }
                            val gridScrollConnection = remember {
                                object : NestedScrollConnection {
                                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                        return if (gridDraggedIndex >= 0) available else Offset.Zero
                                    }
                                }
                            }
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Gray50),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .nestedScroll(gridScrollConnection)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    if (taskVideoList.isEmpty()) {
                                        Text("暂无视频素材", fontSize = 12.sp, color = Gray500)
                                    } else {
                                        taskVideoList.chunked(3).forEachIndexed { rowIdx, row ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                row.forEachIndexed { colIdx, item ->
                                                    val globalIdx = rowIdx * 3 + colIdx
                                                    val isDragging = gridDraggedIndex == globalIdx
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .then(
                                                                    if (isDragging) Modifier.graphicsLayer {
                                                                        translationX = gridDragOffsetX
                                                                        translationY = gridDragOffsetY
                                                                        scaleX = 1.08f
                                                                        scaleY = 1.08f
                                                                        shadowElevation = 10f
                                                                    } else Modifier
                                                                )
                                                                .pointerInput(globalIdx) {
                                                                    detectDragGesturesAfterLongPress(
                                                                        onDragStart = {
                                                                            gridDraggedIndex = globalIdx
                                                                            gridDragOffsetX = 0f
                                                                            gridDragOffsetY = 0f
                                                                        },
                                                                        onDrag = { change, dragAmount ->
                                                                            change.consume()
                                                                            gridDragOffsetX += dragAmount.x
                                                                            gridDragOffsetY += dragAmount.y
                                                                            val col = globalIdx % 3
                                                                            val row = globalIdx / 3
                                                                            // 向右超过阈值 → 与右侧相邻交换
                                                                            if (gridDragOffsetX > gridColWidthPx && col < 2 && globalIdx + 1 < taskVideoList.size) {
                                                                                val targetIdx = globalIdx + 1
                                                                                val temp = taskVideoList[globalIdx]
                                                                                taskVideoList[globalIdx] = taskVideoList[targetIdx]
                                                                                taskVideoList[targetIdx] = temp
                                                                                gridDraggedIndex = targetIdx
                                                                                gridDragOffsetX -= gridColWidthPx
                                                                            }
                                                                            // 向左超过阈值 → 与左侧相邻交换
                                                                            if (gridDragOffsetX < -gridColWidthPx && col > 0) {
                                                                                val targetIdx = globalIdx - 1
                                                                                val temp = taskVideoList[globalIdx]
                                                                                taskVideoList[globalIdx] = taskVideoList[targetIdx]
                                                                                taskVideoList[targetIdx] = temp
                                                                                gridDraggedIndex = targetIdx
                                                                                gridDragOffsetX += gridColWidthPx
                                                                            }
                                                                            // 向下超过阈值 → 与下一行同列交换
                                                                            if (gridDragOffsetY > gridRowHeightPx && globalIdx + 3 < taskVideoList.size) {
                                                                                val targetIdx = globalIdx + 3
                                                                                val temp = taskVideoList[globalIdx]
                                                                                taskVideoList[globalIdx] = taskVideoList[targetIdx]
                                                                                taskVideoList[targetIdx] = temp
                                                                                gridDraggedIndex = targetIdx
                                                                                gridDragOffsetY -= gridRowHeightPx
                                                                            }
                                                                            // 向上超过阈值 → 与上一行同列交换
                                                                            if (gridDragOffsetY < -gridRowHeightPx && globalIdx >= 3) {
                                                                                val targetIdx = globalIdx - 3
                                                                                val temp = taskVideoList[globalIdx]
                                                                                taskVideoList[globalIdx] = taskVideoList[targetIdx]
                                                                                taskVideoList[targetIdx] = temp
                                                                                gridDraggedIndex = targetIdx
                                                                                gridDragOffsetY += gridRowHeightPx
                                                                            }
                                                                        },
                                                                        onDragEnd = { gridDraggedIndex = -1; gridDragOffsetX = 0f; gridDragOffsetY = 0f },
                                                                        onDragCancel = { gridDraggedIndex = -1; gridDragOffsetX = 0f; gridDragOffsetY = 0f }
                                                                    )
                                                                }
                                                        ) {
                                                            VideoThumbnailCard(
                                                                videoUrl = item.fileUrl,
                                                                label = item.label,
                                                                onClick = {
                                                                    videoPreviewUrl = item.fileUrl
                                                                    showVideoPreview = true
                                                                }
                                                            )
                                                        }
                                                        // 序号标记
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopStart)
                                                                .padding(4.dp)
                                                                .size(20.dp)
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .background(Color.Black.copy(alpha = 0.4f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                "${globalIdx + 1}",
                                                                fontSize = 10.sp,
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                                repeat(3 - row.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 刷新按钮
                            Button(
                                onClick = { pmViewModel.refreshMaterials(task.projectId) },
                                enabled = refreshCooldown == 0,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Orange)
                            ) {
                                Text(
                                    if (refreshCooldown > 0) "刷新(${refreshCooldown}s)" else "换一批",
                                    color = Color.White
                                )
                            }

                        }
                    }
                }
                // 合并预览
                val mergeState by pmViewModel.mergeState.collectAsState()
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("合并预览", fontSize = 13.sp, color = Gray500)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (!isClaimed) {
                            // 未领取 → 显示领取按钮 + 示例预览
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            com.task.platform.network.ApiClient.apiService.claimPublishTask(task.id)
                                            isClaimed = true
                                            Toast.makeText(context, "领取成功", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "领取失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusCompleted)
                            ) {
                                Text("领取任务后开始合并", color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // 随机展示3个已有合并效果
                            val historyPreview by pmViewModel.mergeHistory.collectAsState()
                            val samples = historyPreview.take(3)
                            if (samples.isNotEmpty()) {
                                Text("效果预览（已完成的合并）", fontSize = 11.sp, color = Gray500)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    samples.forEach { h ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            VideoThumbnailCard(
                                                videoUrl = h.outputUrl,
                                                label = h.createdAt?.let { formatTime(it) } ?: "",
                                                onClick = {
                                                    videoPreviewUrl = h.outputUrl
                                                    showVideoPreview = true
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    repeat(3 - samples.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        } else {
                            // 已领取 → 显示合并流程

                            // 已提交审核 → 根据状态显示（仅 SUBMITTED/PASSED/REJECTED）
                            if (submissionStatus in listOf("SUBMITTED", "PASSED", "REJECTED")) {
                                val stateText = when (submissionStatus) {
                                    "PASSED" -> "已奖励"
                                    "REJECTED" -> "已拒绝（需要重新提交审核）"
                                    else -> "审核中"
                                }
                                val stateColor = when (submissionStatus) {
                                    "PASSED" -> StatusCompleted
                                    "REJECTED" -> Color.Red
                                    else -> Orange
                                }
                                val stateHint = when (submissionStatus) {
                                    "PASSED" -> if (submissionReward != null) "已发放奖励 ¥${submissionReward}" else "你的发布已审核通过"
                                    "REJECTED" -> "审核未通过，可重新提交"
                                    else -> "你的发布内容已提交，等待管理员审核"
                                }
                                Text(stateText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = stateColor)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stateHint, fontSize = 12.sp, color = Gray500)
                                Spacer(modifier = Modifier.height(8.dp))
                                if (selectedMergeUrl.isNotBlank()) {
                                    Text("发布的视频", fontSize = 11.sp, color = Gray500)
                                    VideoThumbnailCard(
                                        videoUrl = selectedMergeUrl,
                                        label = "已选视频",
                                        onClick = {
                                            videoPreviewUrl = selectedMergeUrl
                                            showVideoPreview = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                // 显示上传的截图
                                Text("上传的截图", fontSize = 11.sp, color = Gray500)
                                Spacer(modifier = Modifier.height(4.dp))
                                submittedScreenshots.chunked(3).forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        row.forEach { url ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Gray100),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val imgUrl = mapImageUrl(url)
                                                AsyncImage(
                                                    model = imgUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            } else {
                                // 正常合并流程
                            val currentUrl = when (val s = mergeState) {
                                is MergeState.Success -> s.url
                                else -> if (selectedMergeUrl.isNotBlank()) selectedMergeUrl else null
                            }
                            if (currentUrl != null) {
                                Button(
                                    onClick = {
                                        videoPreviewUrl = currentUrl
                                        showVideoPreview = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("预览", color = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { saveVideoToGallery(context, currentUrl) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusCompleted)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("保存到相册", color = Color.White)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // 剪辑选项
                            Text("剪辑选项", fontSize = 12.sp, color = Gray500)
                            Spacer(modifier = Modifier.height(4.dp))
                            // 转场
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("转场:", fontSize = 12.sp, color = Gray700, modifier = Modifier.width(48.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    var expanded by remember { mutableStateOf(false) }
                                    val options = listOf(
                                        "none" to "无",
                                        "fade" to "淡入淡出",
                                        "fadeblack" to "黑场过渡",
                                        "fadewhite" to "白场过渡",
                                        "wipeleft" to "左擦",
                                        "wiperight" to "右擦",
                                        "wipeup" to "上擦",
                                        "wipedown" to "下擦",
                                        "slideleft" to "左滑",
                                        "slideright" to "右滑",
                                        "slideup" to "上滑",
                                        "slidedown" to "下滑",
                                        "circlecrop" to "圆形裁剪",
                                        "circleopen" to "圆形展开",
                                        "circleclose" to "圆形收缩",
                                        "dissolve" to "溶解",
                                        "pixelize" to "像素化",
                                        "horzopen" to "水平展开",
                                        "vertopen" to "垂直展开"
                                    )
                                    val selectedLabel = options.find { it.first == transitionType }?.second ?: "无"
                                    OutlinedButton(
                                        onClick = { expanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(selectedLabel, fontSize = 12.sp) }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        options.forEach { (k, v) ->
                                            DropdownMenuItem(onClick = { transitionType = k; expanded = false }, text = { Text(v) })
                                        }
                                    }
                                }
                            }
                            // 渐入渐出
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("渐入渐出:", fontSize = 12.sp, color = Gray700, modifier = Modifier.width(70.dp))
                                Switch(checked = fadeInOut, onCheckedChange = { fadeInOut = it })
                            }
                            // 字幕
                            OutlinedTextField(
                                value = subtitleText,
                                onValueChange = { subtitleText = it },
                                label = { Text("字幕（可选）", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            when (val state = mergeState) {
                                is MergeState.Idle -> {
                                    Button(
                                        onClick = { pmViewModel.mergeVideos(task.projectId, taskVideoList.map { it.id },
                                            transition = transitionType, fadeInOut = fadeInOut, subtitle = subtitleText) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Orange)
                                    ) {
                                        Text("开始合并", color = Color.White)
                                    }
                                }
                                is MergeState.Merging -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = Orange
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("正在合并...", fontSize = 14.sp, color = Gray700)
                                    }
                                }
                                is MergeState.Success -> {
                                    Text("合并完成", fontSize = 14.sp, color = StatusCompleted)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TextButton(
                                        onClick = { pmViewModel.resetMergeState() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("重新合并", fontSize = 12.sp)
                                    }
                                }
                            is MergeState.Error -> {
                                Text("合并失败: ${state.message}", fontSize = 12.sp, color = Color.Red)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { pmViewModel.mergeVideos(task.projectId, taskVideoList.map { it.id }) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                                ) {
                                    Text("重试", color = Color.White)
                                }
                            }
                        }
                            Divider(modifier = Modifier.padding(vertical = 8.dp))

                            // 发布按钮（已领取且尚未发布时显示；不依赖合并URL，支持外部平台发布）
                            if (isClaimed && submissionStatus == "") {
                                Button(
                                    onClick = { showPublishDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                                ) {
                                    Text("发布到平台", color = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            // 提交审核（已发布/记录状态为 MERGED 但尚未提交时显示；不依赖合并URL）
                            if (isClaimed && submissionStatus == "MERGED") {
                                Button(
                                    onClick = { showSubmitDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                                ) {
                                    Text("提交审核", color = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            } // end submitted check

                        } // end else (isClaimed)

                        if (isClaimed && (submissionStatus == "" || submissionStatus == "MERGED")) {
                            TextButton(
                                onClick = {
                                    pmViewModel.loadMergeHistory(task.projectId)
                                    showHistoryGrid = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.History, null, tint = Orange, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("查看合并历史", fontSize = 13.sp, color = Orange)
                            }
                        }
                    }
                }

                // 发布时间 & 状态
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("发布时间", fontSize = 13.sp, color = Gray500)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    formatTime(task.scheduledAt ?: task.createdAt),
                                    fontSize = 14.sp,
                                    color = Gray900
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("状态", fontSize = 13.sp, color = Gray500)
                                Spacer(modifier = Modifier.height(4.dp))
                                PublishStatusTag(status = task.submissionStatus?.takeIf { it.isNotBlank() } ?: task.status)
                            }
                        }

                        // 配额信息：剩余名额 / 总
                        val detailRemain = task.totalQuota - task.usedQuota
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("剩余名额", fontSize = 13.sp, color = Gray500)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$detailRemain / ${task.totalQuota}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (detailRemain > 0) Gray900 else Color(0xFFE53935)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}


// ==================== 详情区块 ====================

@Composable
private fun DetailSection(label: String, content: String) {
    Text(
        text = label,
        fontSize = 13.sp,
        color = Gray500
    )
    Spacer(modifier = Modifier.height(4.dp))
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Gray50,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = content,
            modifier = Modifier.padding(12.dp),
            fontSize = 14.sp,
            color = Gray900,
            lineHeight = 20.sp
        )
    }
}

// ==================== 素材条目 ====================

@Composable
private fun MaterialItem(material: PublishMaterialDTO) {
    val (icon, typeLabel) = when (material.type) {
        "text" -> Icons.Default.Description to "文案"
        "image" -> Icons.Default.Image to "图片"
        "video" -> Icons.Default.Videocam to "视频"
        else -> Icons.Default.AttachFile to material.type
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Gray50)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = typeLabel,
            tint = Orange,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = material.title ?: typeLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Gray900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!material.content.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = material.content,
                    fontSize = 12.sp,
                    color = Gray500,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!material.fileUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "已上传文件",
                    fontSize = 11.sp,
                    color = StatusClaimed
                )
            }
        }
        // 素材类型徽标
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Orange.copy(alpha = 0.1f)
        ) {
            Text(
                text = typeLabel,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 11.sp,
                color = Orange
            )
        }
    }
}

// ==================== 工具函数 ====================

/**
 * 可排序的视频条目
 */
private data class VideoSortItem(
    val id: Long,
    val label: String,
    val fileUrl: String
)

/**
 * 解析平台字符串（逗号分隔）为平台名称列表
 */
private fun parsePlatforms(platforms: String): List<String> {
    if (platforms.isBlank()) return emptyList()
    return platforms.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { code ->
            when (code) {
                "douyin", "1", "抖音" -> "抖音"
                "xiaohongshu", "2", "小红书" -> "小红书"
                else -> code
            }
        }
}

/**
 * 格式化时间字符串为可读格式
 */
private fun formatTime(isoTime: String): String {
    return try {
        // 简单截取，取日期+时间部分
        if (isoTime.length >= 16) {
            isoTime.substring(0, 16).replace("T", " ")
        } else {
            isoTime
        }
    } catch (_: Exception) {
        isoTime
    }
}

// ==================== 视频封面加载 ====================

/**
 * 使用 MediaMetadataRetriever 异步加载视频第一帧作为封面
 */
@Composable
private fun rememberVideoFrameBitmap(videoUrl: String): Bitmap? {
    var bitmap by remember(videoUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(videoUrl) {
        if (videoUrl.isBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val fullUrl = mapImageUrl(videoUrl)
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(fullUrl, HashMap())
                bitmap = retriever.frameAtTime
                retriever.release()
            } catch (_: Exception) { }
        }
    }
    return bitmap
}

/**
 * 视频封面卡片（封面图 + 播放按钮叠加）
 */
@Composable
private fun VideoThumbnailCard(
    videoUrl: String?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val bitmap = if (!videoUrl.isNullOrBlank()) {
        rememberVideoFrameBitmap(videoUrl)
    } else {
        null
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Gray50,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                // 有封面图 → 显示封面
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 无封面 → 显示默认占位
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = "视频",
                        tint = Orange,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(label, fontSize = 12.sp, color = Gray700)
                }
            }
            // 播放按钮覆盖层
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 将相对路径图片 URL 转为可通过 Gateway 访问的完整 URL
 * - 已是 http/https 的完整 URL → 交给 rewriteLocalImageUrl 重写 localhost/127.0.0.1 为上传服务 host
 * - /upload/ 路径 → 拼接 BASE_URL + /api/upload/...（走网关路由到 upload-service）
 * - /uploads/ 路径 → 拼接 BASE_URL + /api/uploads/...（走网关路由到 admin-api）
 * - 其他相对路径 → 直接拼接 BASE_URL
 */
private fun mapImageUrl(url: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) {
        return rewriteLocalImageUrl(url)
    }
    val base = com.task.platform.BuildConfig.BASE_URL.trimEnd('/')
    return if (url.startsWith("/upload/")) {
        "$base/api$url"
    } else if (url.startsWith("/uploads/")) {
        "$base/api$url"
    } else {
        base + (if (url.startsWith("/")) url else "/$url")
    }
}

// ==================== 视频预览弹窗 ====================

@Composable
private fun VideoPreviewDialog(
    url: String,
    onDismiss: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var totalDuration by remember { mutableIntStateOf(0) }
    val videoViewRef = remember { mutableStateOf<VideoView?>(null) }

    // 定期刷新进度
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewRef.value?.let { vv ->
                if (vv.isPlaying) {
                    currentPosition = vv.currentPosition
                    if (totalDuration == 0) {
                        totalDuration = vv.duration
                    }
                }
            }
            kotlinx.coroutines.delay(500)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // 视频画面
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(url)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            totalDuration = mp.duration
                            mp.start()
                            isPlaying = true
                        }
                        setOnCompletionListener {
                            isPlaying = false
                        }
                        videoViewRef.value = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 顶部关闭按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
                }
            }

            // 中央播放/暂停按钮（暂停时显示）
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (!isPlaying) {
                    IconButton(
                        onClick = {
                            videoViewRef.value?.let { vv ->
                                vv.start()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            // 底部控制栏（进度条 + 时间）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Slider(
                    value = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f,
                    onValueChange = { fraction ->
                        val targetMs = (fraction * totalDuration).toInt()
                        videoViewRef.value?.let { vv ->
                            vv.seekTo(targetMs)
                            currentPosition = targetMs
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Orange,
                        activeTrackColor = Orange,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formatDuration(currentPosition), fontSize = 12.sp, color = Color.White)
                    IconButton(
                        onClick = {
                            videoViewRef.value?.let { vv ->
                                if (vv.isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    vv.start()
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(formatDuration(totalDuration), fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

private fun formatDuration(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
/**
 * 保存视频到相册
 */
private fun saveVideoToGallery(context: Context, url: String) {
    val fullUrl = mapImageUrl(url)
    try {
        // 下载视频到缓存
        val fileName = "merge_" + System.currentTimeMillis() + ".mp4"
        val cacheFile = java.io.File(context.cacheDir, fileName)
        
        val thread = Thread {
            try {
                val connection = java.net.URL(fullUrl).openConnection()
                connection.connect()
                val inputStream = connection.getInputStream()
                val outputStream = java.io.FileOutputStream(cacheFile)
                inputStream.copyTo(outputStream)
                outputStream.close()
                inputStream.close()

                // 保存到相册
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/TaskPlatform")
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                )
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        cacheFile.inputStream().copyTo(os)
                    }
                }
                cacheFile.delete()

                android.os.Handler(context.mainLooper).post {
                    android.widget.Toast.makeText(context, "已保存到相册", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.os.Handler(context.mainLooper).post {
                    android.widget.Toast.makeText(context, "保存失败: " + e.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        thread.start()
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "保存失败: " + e.message, android.widget.Toast.LENGTH_SHORT).show()
    }
}

// ==================== 合并历史网格选择弹窗 ====================

@Composable
private fun HistoryGridDialog(
    historyList: List<MergeHistoryVO>,
    onSelect: (MergeHistoryVO) -> Unit,
    onPlay: (MergeHistoryVO) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .systemBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭", tint = Gray900)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("合并历史", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gray900)
                }
                Divider()

                if (historyList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无合并历史", fontSize = 14.sp, color = Gray500)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        val chunked = historyList.chunked(3)
                        items(chunked.size) { rowIdx ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                chunked[rowIdx].forEach { h ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        Column {
                                            // 预览封面
                                            VideoThumbnailCard(
                                                videoUrl = h.outputUrl,
                                                label = h.createdAt?.let { formatTime(it) } ?: "未知",
                                                onClick = { onPlay(h) },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            // 时间 + 选择按钮
                                            Text(
                                                h.createdAt?.let { formatTime(it) } ?: "未知",
                                                fontSize = 9.sp,
                                                color = Gray500,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 2.dp)
                                            )
                                            Button(
                                                onClick = { onSelect(h) },
                                                modifier = Modifier.fillMaxWidth().height(24.dp),
                                                shape = RoundedCornerShape(4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("选择", fontSize = 10.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                                repeat(3 - chunked[rowIdx].size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * 打开发布分享平台（抖音/小红书/微信）
 */
private fun openSharePlatform(context: Context, platforms: String) {
    try {
        val packageName = when {
            platforms.contains("抖音", ignoreCase = true) || platforms.contains("douyin", ignoreCase = true) -> "com.ss.android.ugc.aweme"
            platforms.contains("小红书", ignoreCase = true) || platforms.contains("xhs", ignoreCase = true) || platforms.contains("redbook", ignoreCase = true) -> "com.xingin.xhs"
            platforms.contains("微信", ignoreCase = true) || platforms.contains("wechat", ignoreCase = true) -> "com.tencent.mm"
            else -> null
        }
        if (packageName != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context.startActivity(intent)
                return
            }
        }
        // 默认打开分享面板
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, "已完成视频发布任务")
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "分享到"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "打开失败: " + e.message, android.widget.Toast.LENGTH_SHORT).show()
    }
}


@Composable
private fun SubmitReviewDialog(
    taskId: Long,
    mergedVideoUrl: String,
    context: Context,
    onDismiss: () -> Unit,
    onSubmitted: (List<String>) -> Unit
) {
    var selectedUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadedUrls by remember { mutableStateOf<List<String>>(emptyList()) }

    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        selectedUris = selectedUris + uris
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                // 顶部栏
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭", tint = Gray900)
                    }
                    Text("提交审核", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gray900, modifier = Modifier.weight(1f))
                }
                Divider()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("上传截图（1-9张）", fontSize = 14.sp, color = Gray700)
                    Spacer(modifier = Modifier.height(12.dp))

                    // 截图网格（3列，已上传可删除，末尾有添加格子）
                    val cells: List<android.net.Uri?> = selectedUris + if (selectedUris.size < 9) listOf(null) else emptyList()
                    cells.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            row.forEach { uri ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    if (uri == null) {
                                        // 添加按钮
                                        OutlinedButton(
                                            onClick = { imagePicker.launch("image/*") },
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Orange.copy(alpha = 0.3f))
                                        ) {
                                            Icon(Icons.Default.Add, null, tint = Orange, modifier = Modifier.size(24.dp))
                                        }
                                    } else {
                                        // 已上传的截图
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            // 删除按钮
                                            IconButton(
                                                onClick = { selectedUris = selectedUris.filter { it != uri } },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(22.dp)
                                                    .clip(RoundedCornerShape(11.dp))
                                                    .background(Color.Black.copy(alpha = 0.5f))
                                            ) {
                                                Icon(Icons.Default.Close, "删除", tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Spacer(modifier = Modifier.weight(1f))

                    // 提交按钮
                    Button(
                        onClick = {
                            if (selectedUris.isEmpty()) {
                                Toast.makeText(context, "请至少上传1张截图", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isUploading = true
                            kotlinx.coroutines.GlobalScope.launch {
                                try {
                                    val urls = selectedUris.map { uri ->
                                        val inputStream = context.contentResolver.openInputStream(uri)
                                        val bytes = inputStream?.readBytes() ?: throw Exception("读取失败")
                                        inputStream!!.close()
                                        val requestBody = bytes.toRequestBody("image/*".toMediaType())
                                        val part = okhttp3.MultipartBody.Part.createFormData("file", "screenshot.jpg", requestBody)
                                        val typeBody = "screenshot".toRequestBody("text/plain".toMediaType())
                                        val response = com.task.platform.network.ApiClient.apiService.uploadImage(part, typeBody)
                                        response.data?.relativePath ?: throw Exception("上传失败")
                                    }
                                    uploadedUrls = urls
                                    // 提交审核
                                    val req = mapOf<String, Any>(
                                        "screenshots" to urls,
                                        "mergedVideoUrl" to mergedVideoUrl
                                    )
                                    com.task.platform.network.ApiClient.apiService.submitReview(taskId, req)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "提交成功，等待审核", Toast.LENGTH_SHORT).show()
                                        onSubmitted(urls)
                                    }
                                } catch (e: Exception) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "提交失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    isUploading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                        enabled = !isUploading
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isUploading) "上传中..." else "提交审核", color = Color.White)
                    }
                }
            }
        }
    }
}

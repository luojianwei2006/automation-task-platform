package com.task.platform.ui.publish

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.task.platform.model.PublishMaterialDTO
import com.task.platform.model.PublishTaskDTO
import com.task.platform.viewmodel.PublishViewModel

// ─── 配色体系 ───────────────────────────────────────
private val Orange = Color(0xFFFF8C00)
private val OrangeLight = Color(0xFFFFB347)
private val OrangeBg = Color(0xFFFFF8F0)
private val Gray50 = Color(0xFFFAFAFA)
private val Gray100 = Color(0xFFF5F5F5)
private val Gray300 = Color(0xFFE0E0E0)
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

    // 错误弹窗
    LaunchedEffect(actionError) {
        actionError ?: return@LaunchedEffect
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
                    HallStatItem("全部任务", "${taskList.size}", Icons.Default.Assignment)
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                    HallStatItem(
                        "待领取",
                        "${taskList.count { it.status == "pending" }}",
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
                        "${myTaskList.size}",
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

    // ===== 任务详情弹窗（我的任务） =====
    if (showDetailDialog && detailTask != null) {
        PublishDetailDialog(
            task = detailTask!!,
            onDismiss = {
                showDetailDialog = false
                detailTask = null
            }
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
            .clickable {
                if (isMyTask) onDetail()
            },
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
                PublishStatusTag(status = task.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

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
    val (label, color) = when (status) {
        "pending" -> "待领取" to StatusPending
        "claimed" -> "已领取" to StatusClaimed
        "completed" -> "已完成" to StatusCompleted
        else -> status to Gray500
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

@Composable
private fun PublishDetailDialog(
    task: PublishTaskDTO,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "任务详情",
                fontWeight = FontWeight.Bold,
                color = Gray900,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 项目名
                DetailSection("项目名称", task.projectName.ifBlank { "未命名项目" })

                // 平台标签
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "发布平台",
                    fontSize = 13.sp,
                    color = Gray500
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val platforms = parsePlatforms(task.platforms)
                    platforms.forEach { platform ->
                        PlatformTag(platform = platform)
                    }
                }

                // 文案内容
                if (!task.publishText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailSection("发布文案", task.publishText)
                }

                // 素材列表
                if (task.materials.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "素材列表",
                        fontSize = 13.sp,
                        color = Gray500
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    task.materials.forEach { material ->
                        MaterialItem(material = material)
                    }
                }

                // 发布时间
                Spacer(modifier = Modifier.height(12.dp))
                DetailSection(
                    "发布时间",
                    formatTime(task.scheduledAt ?: task.createdAt)
                )

                // 状态
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "状态：", fontSize = 13.sp, color = Gray500)
                    PublishStatusTag(status = task.status)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Orange, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
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

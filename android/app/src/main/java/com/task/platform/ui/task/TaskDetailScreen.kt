package com.task.platform.ui.task

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.task.platform.model.TaskDTO
import com.task.platform.model.TaskRecordDTO
import com.task.platform.viewmodel.TaskViewModel
import java.util.Locale

// ─── 配色 ───────────────────────────────────────
private val Orange = Color(0xFFFF8C00)
private val OrangeLight = Color(0xFFFFB347)
private val OrangeBg = Color(0xFFFFF8F0)
private val Gray50 = Color(0xFFFAFAFA)
private val Gray100 = Color(0xFFF5F5F5)
private val Gray300 = Color(0xFFE0E0E0)
private val Gray500 = Color(0xFF9E9E9E)
private val Gray700 = Color(0xFF616161)
private val Gray900 = Color(0xFF212121)

@Composable
fun TaskDetailScreen(
    navController: NavController,
    taskId: Long
) {
    val viewModel: TaskViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val currentTask by viewModel.currentTask.collectAsState()
    val currentRecord by viewModel.currentRecord.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    var showAcceptDialog by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        viewModel.resetDetailLoadFlags()
        viewModel.loadTaskDetail(taskId)
        viewModel.loadTaskRecord(taskId)
    }

    LaunchedEffect(actionError) {
        actionError?.let { viewModel.clearActionError() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Gray50)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ===== 顶部栏 =====
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = Gray900)
                    }
                    Text("任务详情", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Gray900, modifier = Modifier.weight(1f))
                }
            }

            when {
                uiState is TaskViewModel.UiState.Loading -> LoadingView()
                uiState is TaskViewModel.UiState.Error -> ErrorView(
                    message = (uiState as TaskViewModel.UiState.Error).message,
                    onRetry = { viewModel.loadTaskDetail(taskId); viewModel.loadTaskRecord(taskId) }
                )
                currentTask != null -> TaskContent(
                    task = currentTask!!,
                    record = currentRecord,
                    onUploadClick = { navController.navigate("screenshot_upload/$taskId") },
                    onAcceptClick = { showAcceptDialog = true }
                )
            }
        }
    }

    // ===== 接受确认对话框 =====
    if (showAcceptDialog) {
        AlertDialog(
            onDismissRequest = { showAcceptDialog = false },
            title = { Text("确认接受任务", fontWeight = FontWeight.Bold) },
            text = { Text("接受任务后请在有效时间内完成并提交截图，超时未提交将视为放弃。") },
            confirmButton = {
                Button(
                    onClick = { showAcceptDialog = false; viewModel.acceptTask(taskId) { viewModel.loadTaskRecord(taskId) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) { Text("确认接受") }
            },
            dismissButton = {
                TextButton(onClick = { showAcceptDialog = false }) { Text("取消") }
            }
        )
    }
}

// ==================== 加载中 ====================

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Orange)
    }
}

// ==================== 错误 ====================

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp), tint = Gray300)
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, color = Gray500)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) { Text("重试") }
        }
    }
}

// ==================== 任务内容 ====================

@Composable
private fun TaskContent(
    task: TaskDTO,
    record: TaskRecordDTO?,
    onUploadClick: () -> Unit,
    onAcceptClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 可滚动内容
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) {
            // 渐变头部
            GradientHeader(task)

            Spacer(modifier = Modifier.height(16.dp))

            // 基本信息卡片
            InfoCard(task)

            // 任务要求卡片
            if (!task.requirements.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                RequirementsCard(task.requirements)
            }

            // 截图示例
            val images = parseImages(task.requirementImages)
            if (images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ScreenshotExamples(images)
            }

            // 状态横幅
            if (record != null) {
                Spacer(modifier = Modifier.height(12.dp))
                StatusBanner(record)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // 固定底部操作栏
        BottomActionBar(record = record, onUploadClick = onUploadClick, onAcceptClick = onAcceptClick)
    }
}

// ==================== 渐变头部 ====================

@Composable
private fun GradientHeader(task: TaskDTO) {
    val remain = task.totalQuota - task.usedQuota
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(brush = Brush.verticalGradient(listOf(Orange, OrangeLight)), shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .padding(20.dp)
    ) {
        Column {
            Text(task.title ?: "未命名任务", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 3)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("任务奖励", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                    Text("¥" + String.format(Locale.getDefault(), "%.2f", task.rewardAmount ?: 0.0), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("剩余名额", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                    Text("$remain / ${task.totalQuota}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ==================== 基本信息卡片 ====================

@Composable
private fun InfoCard(task: TaskDTO) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("基本信息", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gray900)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoChip("平台", platformText(task.platform), Color(0xFFE91E63))
                InfoChip("类型", typeText(task.taskType), Color(0xFF2196F3))
                InfoChip("每日限制", task.dailyLimit?.toString() ?: "不限", Gray500)
            }
            if (!task.locationDesc.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = Orange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(task.locationDesc, fontSize = 14.sp, color = Gray700)
                }
            }
        }
    }
}

// ==================== 任务要求卡片 ====================

@Composable
private fun RequirementsCard(requirements: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("任务要求", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gray900)
            Spacer(modifier = Modifier.height(8.dp))
            Text(requirements, fontSize = 15.sp, color = Gray700, lineHeight = 24.sp)
        }
    }
}

// ==================== 截图示例卡片 ====================

@Composable
private fun ScreenshotExamples(images: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("截图示例", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gray900)
            Spacer(modifier = Modifier.height(12.dp))
            images.forEach { imgUrl ->
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    AsyncImage(model = mapImageUrl(imgUrl), contentDescription = "截图示例", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ==================== 状态横幅 ====================

@Composable
private fun StatusBanner(record: TaskRecordDTO) {
    val (bgColor, text, icon) = when (record.status) {
        0 -> Triple(OrangeBg, "进行中", Icons.Default.PlayArrow)
        1 -> Triple(Color(0xFFE3F2FD), "审核中", Icons.Default.HourglassEmpty)
        2 -> Triple(Color(0xFFE8F5E9), "已通过", Icons.Default.CheckCircle)
        3 -> Triple(Color(0xFFFFEBEE), "未通过", Icons.Default.Cancel)
        4 -> Triple(Gray100, "已超时", Icons.Default.Schedule)
        else -> Triple(Gray100, "未知", Icons.Default.Info)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Orange, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("任务状态：$text", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
                record.reviewResult?.let { reason ->
                    Text(reason, fontSize = 12.sp, color = Gray500, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

// ==================== 底部操作栏 ====================

@Composable
private fun BottomActionBar(
    record: TaskRecordDTO?,
    onUploadClick: () -> Unit,
    onAcceptClick: () -> Unit
) {
    Surface(shadowElevation = 8.dp, color = Color.White) {
        when {
            record == null -> {
                Button(
                    onClick = onAcceptClick,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) { Text("接受任务", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
            record.status == 0 -> {
                Button(
                    onClick = onUploadClick,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Icon(Icons.Default.Upload, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("上传截图", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            record.status == 1 -> {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Orange, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("审核中，请耐心等待", fontSize = 16.sp, color = Orange, fontWeight = FontWeight.Medium)
                }
            }
            record.status == 2 -> {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("审核通过 · 奖励已发放", fontSize = 16.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
                }
            }
            record.status == 3 -> {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cancel, null, tint = Color(0xFFE53935))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("审核未通过", fontSize = 16.sp, color = Color(0xFFE53935), fontWeight = FontWeight.Medium)
                }
            }
            record.status == 4 -> {
                // 超时/放弃 — 可以重新接受
                Button(
                    onClick = onAcceptClick,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) { Text("重新接受", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ==================== 工具组件 ====================

@Composable
private fun InfoChip(label: String, value: String, color: Color) {
    Column {
        Text(label, fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.1f)) {
            Text(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

private fun platformText(platform: Int) = when (platform) { 1 -> "抖音"; 2 -> "小红书"; else -> "全平台" }
private fun typeText(taskType: Int) = when (taskType) { 1 -> "点赞"; 2 -> "评论"; else -> "其他" }

// ==================== 图片解析 ====================

private fun parseImages(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val type = object : TypeToken<List<String>>() {}.type
        Gson().fromJson(json, type) ?: emptyList()
    } catch (e: Exception) { emptyList() }
}

private fun mapImageUrl(url: String): String {
    // 已是完整 URL
    if (url.startsWith("http://") || url.startsWith("https://")) {
        return url.replace("localhost", "10.0.2.2").replace("127.0.0.1", "10.0.2.2")
    }
    // 相对路径，拼接 base URL（去掉尾部 /）
    val base = com.task.platform.BuildConfig.BASE_URL.trimEnd('/')
    return base + (if (url.startsWith("/")) url else "/$url")
}

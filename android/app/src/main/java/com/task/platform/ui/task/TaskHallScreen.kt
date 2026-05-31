package com.task.platform.ui.task

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
import com.task.platform.model.TaskDTO
import com.task.platform.viewmodel.TaskViewModel

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

/**
 * 任务大厅 — 高端橙色渐变设计
 */
@Composable
fun TaskHallScreen(
    onTaskClick: (Long) -> Unit
) {
    val viewModel: TaskViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val taskList by viewModel.taskList.collectAsState()
    var selectedPlatform by remember { mutableIntStateOf(0) }
    var selectedType by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.loadTasks() }

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
                    text = "任务大厅",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "完成任务，赚取奖励",
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
                    HallStatItem("任务总数", "${taskList.size}", Icons.Default.Assignment)
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                    HallStatItem("进行中", "${taskList.count { it.status == 1 }}", Icons.Default.PlayArrow)
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                    HallStatItem("今日新增", "${taskList.size}", Icons.Default.NewReleases)
                }
            }
        }

        // ===== 筛选栏 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChipGroup(
                label = "平台",
                options = listOf("全部" to 0, "抖音" to 1, "小红书" to 2),
                selected = selectedPlatform,
                onSelect = { selectedPlatform = it }
            )
            FilterChipGroup(
                label = "类型",
                options = listOf("全部" to 0, "点赞" to 1, "评论" to 2),
                selected = selectedType,
                onSelect = { selectedType = it }
            )
        }

        // ===== 任务列表 =====
        when (uiState) {
            is TaskViewModel.UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Orange)
                }
            }
            else -> {
                if (taskList.isEmpty()) {
                    HallEmptyView()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(taskList, key = { it.id }) { task ->
                            TaskCard(task = task, onClick = { onTaskClick(task.id) })
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
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

// ==================== 筛选标签组 ====================

@Composable
private fun FilterChipGroup(
    label: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        options.forEach { (text, value) ->
            val isSelected = selected == value
            Surface(
                modifier = Modifier.clickable { onSelect(value) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) Orange else Gray100,
                shadowElevation = if (isSelected) 2.dp else 0.dp
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else Gray700
                )
            }
        }
    }
}

// ==================== 任务卡片 ====================

@Composable
private fun TaskCard(
    task: TaskDTO,
    onClick: () -> Unit
) {
    val remainCount = task.totalQuota - task.usedQuota

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 顶部：标题 + 奖励
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
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
                        Icons.Default.TaskAlt,
                        contentDescription = null,
                        tint = Orange,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // 中间：标题 + 描述
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title ?: "未命名任务",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Gray900,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!task.requirements.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = task.requirements,
                            fontSize = 13.sp,
                            color = Gray500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 右侧：奖励金额
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "¥" + String.format(java.util.Locale.getDefault(), "%.2f", task.rewardAmount ?: 0.0),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Orange
                    )
                    if (remainCount >= 0) {
                        Text(
                            text = "剩余 $remainCount / ${task.totalQuota}",
                            fontSize = 11.sp,
                            color = if (remainCount > 0) Gray500 else Color(0xFFE53935)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 底部：标签行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 平台标签
                val (platformLabel, platformColor) = when (task.platform) {
                    1 -> "抖音" to Color(0xFFE91E63)
                    2 -> "小红书" to Color(0xFFFF2442)
                    else -> "全平台" to Orange
                }
                TaskTag(text = platformLabel, color = platformColor)

                // 类型标签
                val (typeLabel, typeColor) = when (task.taskType) {
                    1 -> "点赞" to Color(0xFF2196F3)
                    2 -> "评论" to Color(0xFF4CAF50)
                    else -> "其他" to Gray500
                }
                TaskTag(text = typeLabel, color = typeColor)

                Spacer(modifier = Modifier.weight(1f))

                // 定位图标
                if (!task.locationDesc.isNullOrBlank()) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Gray500
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = task.locationDesc,
                        fontSize = 11.sp,
                        color = Gray500,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ==================== 标签组件 ====================

@Composable
private fun TaskTag(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

// ==================== 空状态 ====================

@Composable
private fun HallEmptyView() {
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
                text = "暂无任务",
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

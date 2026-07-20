package com.task.platform.ui.task

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import com.task.platform.ui.components.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.task.platform.ui.theme.statusColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.task.platform.model.TaskDTO
import com.task.platform.viewmodel.TaskViewModel
import java.util.Locale

// ─── 配色 ─────────────────────────────────────
private val Gray50 = Color(0xFFFAFAFA)
private val Gray100 = Color(0xFFF5F5F5)
private val Gray300 = Color(0xFFE0E0E0)
private val Gray400 = Color(0xFFBDBDBD)
private val Gray500 = Color(0xFF9E9E9E)
private val Gray700 = Color(0xFF616161)
private val Gray900 = Color(0xFF212121)
private val GreenLight = Color(0xFFE8F5E9)
private val Green = Color(0xFF4CAF50)
private val RedLight = Color(0xFFFFEBEE)
private val Red = Color(0xFFE53935)
private val BlueLight = Color(0xFFE3F2FD)
private val Blue = Color(0xFF42A5F5)

/**
 * 我的任务列表页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    navController: NavController
) {
    val viewModel: TaskViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val myTaskList by viewModel.myTaskList.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadMyTasks()
    }

    // ── 下拉刷新 ──
    val pullRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            viewModel.loadMyTasks()
            pullRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.TaskAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("我的任务", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Gray50
    ) { padding ->

        when (uiState) {
            is TaskViewModel.UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
            is TaskViewModel.UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = (uiState as TaskViewModel.UiState.Error).message ?: "加载失败",
                            color = Red,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { viewModel.loadMyTasks() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("重试")
                        }
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .nestedScroll(pullRefreshState.nestedScrollConnection)
                ) {
                    if (myTaskList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Gray50),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassEmpty,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "还没有接取任何任务",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Gray700
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "去任务大厅看看吧",
                                    fontSize = 14.sp,
                                    color = Gray500
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                Text(
                                    text = "共 ${myTaskList.size} 个任务",
                                    fontSize = 13.sp,
                                    color = Gray500,
                                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                                )
                            }
                            items(myTaskList, key = { it.id }) { task ->
                                MyTaskCard(
                                    task = task,
                                    onClick = { navController.navigate("task_detail/${task.id}") }
                                )
                            }
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
}

// ==================== 任务卡片 ====================

@Composable
private fun MyTaskCard(
    task: TaskDTO,
    onClick: () -> Unit
) {
    val remain = task.totalQuota - task.usedQuota
    val progress = if (task.totalQuota > 0) {
        task.usedQuota.toFloat() / task.totalQuota.toFloat()
    } else 0f

    // 左侧竖条颜色
    val leftBarColor = when (task.platform) {
        1 -> Color(0xFF000000) // 抖音黑
        2 -> Color(0xFFFF2442) // 小红书红
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧彩色竖条
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(leftBarColor, leftBarColor.copy(alpha = 0.3f))
                        ),
                        shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                    )
            )

            // 内容区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                // ── 第一行：标题 + 金额 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title ?: "未命名任务",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Gray900,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    // 奖励金额
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        val rewardText = remember(task.rewardAmount) {
                            "¥" + String.format(Locale.getDefault(), "%.2f", task.rewardAmount ?: 0.0)
                        }
                        Text(
                            text = rewardText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── 第二行：标签 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val platformLabel = when (task.platform) {
                        1 -> "抖音"
                        2 -> "小红书"
            3 -> "微信视频号"
                        else -> if (task.platform == 0) "广告" else "全平台"
                    }
                    TaskTag(text = platformLabel, bgColor = Gray100, textColor = Gray700)

                    val typeLabel = when (task.taskType) {
                        1 -> "点赞"
                        2 -> "评论"
                        0 -> "观看"
                        else -> "其他"
                    }
                    TaskTag(text = typeLabel, bgColor = Gray100, textColor = Gray700)

                    // 状态标签（我的任务：使用用户记录状态语义）
                    val statusInfo = getRecordStatusInfo(task.recordStatus ?: 0)
                    TaskTag(text = statusInfo.first, bgColor = statusInfo.second, textColor = statusInfo.third)
                }

                Spacer(Modifier.height(10.dp))

                // ── 第三行：进度条 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (remain > 0) Green else Red,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (remain > 0) "剩余 $remain 个" else "已满",
                            fontSize = 13.sp,
                            color = if (remain > 0) Gray700 else Red
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // 自定义进度条（兼容旧版 Compose）
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${task.usedQuota}/${task.totalQuota}",
                            fontSize = 12.sp,
                            color = Gray400
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Gray100)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = progress)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 标签组件 ====================

@Composable
private fun TaskTag(text: String, bgColor: Color, textColor: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = bgColor) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ==================== 状态映射 ====================

@Composable
private fun getTaskStatusInfo(status: Int): Triple<String, Color, Color> {
    return when (status) {
        1 -> Triple("进行中", GreenLight, Green)
        2 -> Triple("暂停", Color(0xFFFFF3E0), MaterialTheme.colorScheme.primary)
        3 -> Triple("结束", RedLight, Red)
        4 -> Triple("拒绝", RedLight, Red)
        else -> Triple("待审核", BlueLight, Blue)
    }
}

/**
 * 用户记录状态 → 状态标签（记录语义，与任务发布状态不同）
 * 0=进行中 1=待审核 2=已通过 3=已拒绝 4=已超时
 */
@Composable
private fun getRecordStatusInfo(status: Int): Triple<String, Color, Color> {
    return when (status) {
        0 -> Triple("进行中", Color(0xFFE8F5E9), Color(0xFF4CAF50))   // 已接取未提交-绿
        1 -> Triple("待审核", MaterialTheme.statusColors.reviewing.container, MaterialTheme.statusColors.reviewing.main)   // 已提交待审核-蓝
        2 -> Triple("已通过", Color(0xFFE8F5E9), Color(0xFF2E7D32))   // 审核通过-深绿
        3 -> Triple("已拒绝", Color(0xFFFFEBEE), Color(0xFFE53935))   // 拒绝-红
        4 -> Triple("已超时", Color(0xFFF5F5F5), Color(0xFF9E9E9E))   // 超时/放弃-灰
        else -> Triple("进行中", Color(0xFFE8F5E9), Color(0xFF4CAF50))
    }
}

package com.task.platform.ui.earnings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.task.platform.model.EarningsRecord
import com.task.platform.navigation.TaskRoutes
import com.task.platform.viewmodel.EarningsViewModel
import kotlinx.coroutines.flow.*

// ─── 配色（同包 TransactionRecordsScreen 复用，故用 internal） ─────────
internal val HallOrange = Color(0xFFFF8C00)
internal val HallOrangeLight = Color(0xFFFFB347)
internal val HallOrangeBg = Color(0xFFFFF8F0)
internal val Gray50 = Color(0xFFFAFAFA)
internal val Gray100 = Color(0xFFF5F5F5)
internal val Gray300 = Color(0xFFE0E0E0)
internal val Gray500 = Color(0xFF9E9E9E)
internal val Gray700 = Color(0xFF616161)
internal val Gray900 = Color(0xFF212121)

/** 收入绿色 */
internal val IncomeGreen = Color(0xFF4CAF50)
/** 支出红色 */
internal val ExpenseRed = Color(0xFFE53935)

/**
 * 收益中心屏幕（底部"收益" Tab）
 * 展示橙色渐变头部：总余额 + 三列统计 + 提现按钮，
 * 并在下方提供类型筛选 Tab 与完整收益明细列表（支持上拉加载更多）。
 * 明细列表 UI 复用同包 TransactionRecordsScreen 的 EarningsRecordItem / EarningsEmptyView。
 */
@Composable
fun EarningsScreen(
    navController: NavController,
    viewModel: EarningsViewModel = hiltViewModel()
) {
    val earningsSummary by viewModel.earningsSummary.collectAsState()
    val earningsRecords by viewModel.earningsRecords.collectAsState()
    // 收益 Tab 仅展示奖励记录，过滤掉提现(type=5)
    val displayRecords = earningsRecords.filter { it.type != 5 }
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf("全部", "任务收益", "邀请奖励", "其他")
    val tabTypes = listOf<Int?>(null, 1, 2, 4)

    // 列表状态，用于上拉加载更多
    val listState = rememberLazyListState()

    // 初始加载：概览 + 明细（与流水记录页一致）
    LaunchedEffect(Unit) {
        viewModel.loadEarningsSummary()
        viewModel.loadEarningsRecords(refresh = true)
    }

    // 上拉加载更多：滚动接近列表底部时触发
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= layoutInfo.totalItemsCount - 2
        }.distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadMore() }
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
                        colors = listOf(HallOrange, HallOrangeLight)
                    ),
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column {
                Text(
                    text = "我的收益",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                // 总余额
                Text(
                    text = "总余额（元）",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format("%.2f", earningsSummary?.availableBalance ?: 0.0),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        lineHeight = 38.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "元",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 三列统计
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EarningsStatItem(
                        label = "今日收入",
                        value = "¥${String.format("%.2f", earningsSummary?.todayEarnings ?: 0.0)}",
                        icon = Icons.Default.TrendingUp
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                    EarningsStatItem(
                        label = "累计收入",
                        value = "¥${String.format("%.2f", earningsSummary?.totalEarnings ?: 0.0)}",
                        icon = Icons.Default.AccountBalanceWallet
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                    EarningsStatItem(
                        label = "可提现",
                        value = "¥${String.format("%.2f", earningsSummary?.availableBalance ?: 0.0)}",
                        icon = Icons.Default.Savings
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 提现按钮
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            navController.navigate(TaskRoutes.WITHDRAW)
                        },
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, HallOrange)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "立即提现",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = HallOrange
                        )
                    }
                }
            }
        }

        // ===== 类型筛选 Tab 行 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = selectedTab == index
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) HallOrangeBg else Color.Transparent,
                    modifier = Modifier
                        .height(38.dp)
                        .clickable {
                            selectedTab = index
                            viewModel.loadEarningsRecords(type = tabTypes[index], refresh = true)
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) HallOrange else Gray500
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Gray100, thickness = 1.dp)

        // ===== 收益明细列表 =====
        if (isLoading && displayRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = HallOrange)
            }
        } else if (displayRecords.isEmpty()) {
            Box(modifier = Modifier.weight(1f)) {
                EarningsEmptyView()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(displayRecords) { record ->
                    EarningsRecordItem(record = record)
                    if (displayRecords.indexOf(record) < displayRecords.size - 1) {
                        HorizontalDivider(
                            color = Gray100,
                            thickness = 1.dp
                        )
                    }
                }
                // 加载更多
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = HallOrange,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ==================== 统计项 ====================

@Composable
private fun EarningsStatItem(
    label: String,
    value: String,
    icon: ImageVector
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
            fontSize = 16.sp,
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

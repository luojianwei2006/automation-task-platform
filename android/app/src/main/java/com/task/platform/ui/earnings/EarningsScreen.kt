package com.task.platform.ui.earnings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.task.platform.model.EarningsRecord
import com.task.platform.viewmodel.EarningsViewModel

// ─── 配色（复用 TaskHallScreen） ───────────────────────────────────────
private val HallOrange = Color(0xFFFF8C00)
private val HallOrangeLight = Color(0xFFFFB347)
private val HallOrangeBg = Color(0xFFFFF8F0)
private val Gray50 = Color(0xFFFAFAFA)
private val Gray100 = Color(0xFFF5F5F5)
private val Gray300 = Color(0xFFE0E0E0)
private val Gray500 = Color(0xFF9E9E9E)
private val Gray700 = Color(0xFF616161)
private val Gray900 = Color(0xFF212121)

/** 收入绿色 */
private val IncomeGreen = Color(0xFF4CAF50)
/** 支出红色 */
private val ExpenseRed = Color(0xFFE53935)

/**
 * 收益中心屏幕
 * 橙色渐变头部（总余额 + 统计 + 提现按钮）+ 标签页切换 + 收益明细列表
 */
@Composable
fun EarningsScreen(
    navController: NavController,
    viewModel: EarningsViewModel = hiltViewModel()
) {
    val earningsSummary by viewModel.earningsSummary.collectAsState()
    val earningsRecords by viewModel.earningsRecords.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    // 初始加载
    LaunchedEffect(Unit) {
        viewModel.loadEarningsSummary()
        viewModel.loadEarningsRecords(refresh = true)
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
                        label = "今日收益",
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
                        label = "累计收益",
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
                            navController.navigate("withdraw")
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

        // ===== 标签页切换 =====
        val tabs = listOf("全部", "任务收益", "邀请奖励", "其他")
        val tabTypes = listOf(null, 1, 2, 4)

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
        if (isLoading && earningsRecords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = HallOrange)
            }
        } else if (earningsRecords.isEmpty()) {
            EarningsEmptyView()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(earningsRecords) { record ->
                    EarningsRecordItem(record = record)
                    if (earningsRecords.indexOf(record) < earningsRecords.size - 1) {
                        HorizontalDivider(
                            color = Gray100,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 0.dp)
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

// ==================== 收益记录项 ====================

@Composable
private fun EarningsRecordItem(record: EarningsRecord) {
    val isIncome = record.amount >= 0
    val icon = when (record.type) {
        1 -> Icons.Default.Payments
        2 -> Icons.Default.Celebration
        3 -> Icons.Default.AccountBalanceWallet
        else -> Icons.Default.ReceiptLong
    }
    val iconBg = when (record.type) {
        1 -> Color(0xFFE8F5E9)
        2 -> Color(0xFFFFF3E0)
        3 -> Color(0xFFFFEBEE)
        else -> Color(0xFFE3F2FD)
    }
    val iconTint = when (record.type) {
        1 -> IncomeGreen
        2 -> HallOrange
        3 -> ExpenseRed
        else -> Color(0xFF42A5F5)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 类型图标
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // 描述文字 + 日期
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.description,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Gray900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = record.createdAt,
                fontSize = 13.sp,
                color = Gray500
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 金额
        Text(
            text = if (isIncome) "+${String.format("%.2f", record.amount)}" else String.format("%.2f", record.amount),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (isIncome) IncomeGreen else ExpenseRed
        )
    }
}

// ==================== 空状态 ====================

@Composable
private fun EarningsEmptyView() {
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
                text = "暂无收益记录",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Gray500
            )
            Text(
                text = "完成任务后收益将在此展示",
                fontSize = 16.sp,
                color = Gray500,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

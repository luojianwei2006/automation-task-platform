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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.task.platform.model.EarningsRecord
import com.task.platform.viewmodel.EarningsViewModel
import com.task.platform.ui.components.LoadingIndicator
import androidx.compose.material3.MaterialTheme

/**
 * 流水记录页（独立页面）
 * 由 ProfileScreen 的"流水记录"菜单项通过 TaskRoutes.EARNINGS_RECORDS 进入。
 * 顶部返回箭头 + 标题"流水记录" + 类型标签页 + 收益明细列表。
 * 配色复用同包 EarningsScreen.kt 中的 internal 常量。
 */
@Composable
fun TransactionRecordsScreen(
    navController: NavController,
    viewModel: EarningsViewModel = hiltViewModel()
) {
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
        // ===== 顶部渐变头部（返回箭头 + 标题） =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                    ),
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                Text(
                    text = "流水记录",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // ===== 标签页切换 =====
        val tabs = listOf("全部", "任务收益", "邀请奖励", "提现", "其他")
        val tabTypes = listOf(null, 1, 2, 5, 4)

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
                    color = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
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
                            color = if (selected) MaterialTheme.colorScheme.primary else Gray500
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
                LoadingIndicator()
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
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ==================== 收益记录项 ====================

@Composable
internal fun EarningsRecordItem(record: EarningsRecord) {
    val isIncome = record.amount >= 0
    val icon = when (record.type) {
        1 -> Icons.Default.Payments
        2 -> Icons.Default.Celebration
        3 -> Icons.Default.AccountBalanceWallet
        5 -> Icons.Default.ArrowUpward
        else -> Icons.Default.ReceiptLong
    }
    val iconBg = when (record.type) {
        1 -> Color(0xFFE8F5E9)
        2 -> Color(0xFFFFF3E0)
        3 -> Color(0xFFFFEBEE)
        5 -> Color(0xFFFFEBEE)
        else -> Color(0xFFE3F2FD)
    }
    val iconTint = when (record.type) {
        1 -> IncomeGreen
        2 -> MaterialTheme.colorScheme.primary
        3 -> ExpenseRed
        5 -> ExpenseRed
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
                text = if (record.type == 5 && (record.description ?: "").isBlank()) "提现" else (record.description ?: ""),
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
internal fun EarningsEmptyView() {
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
                text = "暂无流水记录",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Gray500
            )
            Text(
                text = "余额变动将在此显示",
                fontSize = 16.sp,
                color = Gray500,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

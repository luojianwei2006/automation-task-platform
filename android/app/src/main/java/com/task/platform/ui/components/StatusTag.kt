package com.task.platform.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.task.platform.ui.theme.Shape
import com.task.platform.ui.theme.statusColors

/**
 * 状态类型枚举，对应 [MaterialTheme.statusColors] 的五个语义状态。
 */
enum class StatusType {
    Reviewing, // 审核中（蓝）
    Approved,  // 已通过（绿）
    Rejected,  // 已拒绝（红）
    Pending,   // 待领取（橙）
    Timeout    // 超时（灰）
}

/**
 * 统一状态标签。
 *
 * - 圆角 [Shape.radiusSm]（8dp）
 * - 语义色「浅底 container + 同色 content 字」（深色模式由 [MaterialTheme.statusColors] 自动切换为深色 container + 浅字）
 * - 内边距 水平 8dp / 垂直 4dp，文字 labelMedium Medium
 *
 * 页面禁止直接写 `0xFF42A5F5` 等状态色，必须经由本组件（架构 §7.4）。
 *
 * @param type 状态类型
 */
@Composable
fun StatusTag(type: StatusType, modifier: Modifier = Modifier) {
    val set = when (type) {
        StatusType.Reviewing -> MaterialTheme.statusColors.reviewing
        StatusType.Approved -> MaterialTheme.statusColors.approved
        StatusType.Rejected -> MaterialTheme.statusColors.rejected
        StatusType.Pending -> MaterialTheme.statusColors.pending
        StatusType.Timeout -> MaterialTheme.statusColors.timeout
    }
    Surface(
        modifier = modifier,
        shape = Shape.radiusSm,
        color = set.container
    ) {
        androidx.compose.material3.Text(
            text = when (type) {
                StatusType.Reviewing -> "审核中"
                StatusType.Approved -> "已通过"
                StatusType.Rejected -> "已拒绝"
                StatusType.Pending -> "待领取"
                StatusType.Timeout -> "超时"
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = set.content
        )
    }
}

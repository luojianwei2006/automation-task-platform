package com.task.platform.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 统一空状态。
 *
 * - 图标 `colorScheme.outline`，尺寸 64.dp（P2-2 升级：可替换为品牌插画，当前占位）
 * - 文案 `colorScheme.onSurfaceVariant` bodyLarge 居中
 *
 * @param message 提示文案
 * @param modifier 修饰（通常 fillMaxSize 居中）
 * @param icon    图标，默认 [Icons.Default.Inbox]
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    icon: ImageVector = Icons.Default.Inbox
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // TODO 插画：P2-2 计划以品牌风格插画替换占位图标（不阻塞编译）
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

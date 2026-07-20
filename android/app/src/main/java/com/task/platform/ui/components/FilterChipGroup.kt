package com.task.platform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.task.platform.ui.theme.Shape

/**
 * 复用型筛选 Chip 组（替代各页手写 Chip）。
 *
 * - 每个 Chip 圆角 [Shape.radiusMd]（12dp）
 * - 选中底 `primary`，文字 `onPrimary`；未选中底 `surfaceVariant`，文字 `onSurfaceVariant`
 * - 多选：以 [selected] 集合表示，点击触发 [onToggle]
 *
 * @param options 选项文本列表
 * @param selected 当前选中集合
 * @param onToggle 切换某选项选中态的回调
 * @param modifier 修饰
 */
@Composable
fun FilterChipGroup(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { text ->
            val isSelected = selected.contains(text)
            Surface(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clickable { onToggle(text) },
                shape = Shape.radiusMd,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

package com.task.platform.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.task.platform.ui.theme.Shape

/**
 * 统一卡片容器。
 *
 * - 圆角固定 [Shape.radiusLg]（16dp）
 * - 背景 [MaterialTheme.colorScheme.surface]
 * - 轻量浮起阴影 1.dp
 * - 可选描边（border = true 时描 `outlineVariant`）
 *
 * @param modifier       外层修饰
 * @param onClick        可选点击回调；非空时整卡可点
 * @param border         是否显示描边
 * @param contentPadding 内容内边距，默认 16.dp
 * @param content        卡片内容（ColumnScope）
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = Shape.radiusLg,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        border = if (border) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        }
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(contentPadding)
        ) {
            content()
        }
    }
}

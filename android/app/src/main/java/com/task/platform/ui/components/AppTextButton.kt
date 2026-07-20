package com.task.platform.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.task.platform.ui.theme.Shape

/**
 * 统一文字按钮（次级操作）。
 *
 * - 无背景，文字色 `colorScheme.primary`
 * - 圆角 [Shape.radiusSm]（8dp）
 * - 高度 40.dp，文字 bodyMedium Medium
 *
 * @param text     按钮文字
 * @param onClick  点击回调
 * @param modifier 修饰
 * @param enabled  是否可用
 */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        shape = Shape.radiusSm,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

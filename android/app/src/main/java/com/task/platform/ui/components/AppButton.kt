package com.task.platform.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.task.platform.ui.theme.Shape

/**
 * 统一主按钮。
 *
 * - 背景 `colorScheme.primary`，文字 `onPrimary`（白字）
 * - 圆角 [Shape.radiusSm]（8dp）
 * - 默认填充最大宽，高度 48.dp，文字 bodyLarge Medium
 * - 禁用态背景 `surfaceVariant`，文字 `onSurfaceVariant`
 *
 * @param text     按钮文字
 * @param onClick  点击回调
 * @param modifier 修饰（默认 fillMaxWidth）
 * @param enabled  是否可用
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = Shape.radiusSm,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

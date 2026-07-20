package com.task.platform.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 统一加载指示器。
 *
 * - Material3 [CircularProgressIndicator]，`color = colorScheme.primary`
 * - strokeWidth 4.dp，size 40dp，居中
 * - 替代全站裸 `CircularProgressIndicator()`（架构 §7.6）
 *
 * @param modifier 修饰（默认 fillMaxSize 居中）
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier.fillMaxSize(),
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = (size * 0.1f).coerceAtLeast(2.dp)
        )
    }
}

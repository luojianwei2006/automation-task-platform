package com.task.platform.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * 应用主题入口。
 *
 * 封装 Material3 [MaterialTheme]，按系统深浅模式注入 [lightScheme] / [darkScheme]，
 * 并通过 [LocalStatusColors] 暴露语义状态色 [AppStatusColors]。
 * 在 `MainActivity.setContent` 中替换默认 `MaterialTheme { ... }`。
 *
 * @param darkTheme 是否深色模式，默认跟随系统（架构决策：不引入 Material You 动态取色，
 *                   预留扩展位）。
 * @param content   页面内容
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkScheme else lightScheme
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography
    ) {
        CompositionLocalProvider(
            LocalStatusColors provides statusColors,
            content = content
        )
    }
}

package com.task.platform.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 单个状态色的一组语义色值。
 *
 * @param main     状态的权威主色（用于强调点、图标等）
 * @param container 标签/横幅的底色
 * @param content  标签/横幅上的文字色（浅色模式为深字，深色模式为浅字）
 */
data class StatusColorSet(
    val main: Color,
    val container: Color,
    val content: Color
)

/**
 * 语义状态色集合 —— 收敛现状「审核中蓝/已通过绿/已拒绝红/待领取橙/超时灰」分歧。
 * 取值依据 PRD §4.1.2（浅色：浅底 + 深字）与 §4.4（深色：深色 container + 浅字）。
 */
data class AppStatusColors(
    val reviewing: StatusColorSet, // 审核中 蓝 0xFF42A5F5
    val approved: StatusColorSet,  // 已通过 绿 0xFF4CAF50
    val rejected: StatusColorSet,  // 已拒绝 红 0xFFE53935
    val pending: StatusColorSet,   // 待领取 橙 0xFFFF8C00
    val timeout: StatusColorSet    // 超时 灰 0xFF9E9E9E
)

/** 浅色模式状态色（浅底 + 深字），依据 PRD §4.1.2。 */
val LightStatusColors: AppStatusColors = AppStatusColors(
    reviewing = StatusColorSet(
        main = Color(0xFF42A5F5),
        container = Color(0xFFE3F2FD),
        content = Color(0xFF0D47A1)
    ),
    approved = StatusColorSet(
        main = Color(0xFF4CAF50),
        container = Color(0xFFE8F5E9),
        content = Color(0xFF1B5E20)
    ),
    rejected = StatusColorSet(
        main = Color(0xFFE53935),
        container = Color(0xFFFFEBEE),
        content = Color(0xFFB71C1C)
    ),
    pending = StatusColorSet(
        main = Color(0xFFFF8C00),
        container = Color(0xFFFFF3E0),
        content = Color(0xFF5C2E00)
    ),
    timeout = StatusColorSet(
        main = Color(0xFF9E9E9E),
        container = Color(0xFFF5F5F5),
        content = Color(0xFF424242)
    )
)

/** 深色模式状态色（深色 container + 浅字），依据 PRD §4.4。 */
val DarkStatusColors: AppStatusColors = AppStatusColors(
    reviewing = StatusColorSet(
        main = Color(0xFF42A5F5),
        container = Color(0xFF0D2A3D),
        content = Color(0xFF90CAF9)
    ),
    approved = StatusColorSet(
        main = Color(0xFF4CAF50),
        container = Color(0xFF0E2E16),
        content = Color(0xFFA5D6A7)
    ),
    rejected = StatusColorSet(
        main = Color(0xFFE53935),
        container = Color(0xFF2E1212),
        content = Color(0xFFFFAB91)
    ),
    pending = StatusColorSet(
        main = Color(0xFFFF8C00),
        container = Color(0xFF2E1E00),
        content = Color(0xFFFFCC80)
    ),
    timeout = StatusColorSet(
        main = Color(0xFF9E9E9E),
        container = Color(0xFF202020),
        content = Color(0xFFE0E0E0)
    )
)

/**
 * 状态色 CompositionLocal，由 [AppTheme] 注入并随深浅模式切换。
 */
val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/**
 * 便捷扩展：在 Composable 作用域内读取当前语义状态色。
 *
 * 用法：`MaterialTheme.statusColors.reviewing.container`
 */
val MaterialTheme.statusColors: AppStatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current

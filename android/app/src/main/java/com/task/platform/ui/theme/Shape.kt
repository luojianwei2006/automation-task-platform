package com.task.platform.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 圆角尺度阶梯 —— 依据 PRD §4.2。
 * 全站禁止 `RoundedCornerShape(N.dp)` 字面量，一律引用 [Shape.radiusXl] 等。
 *
 * - radiusSm = 8dp  按钮 / Tag / Chip / 小卡片
 * - radiusMd = 12dp 输入框 / 中段容器
 * - radiusLg = 16dp AppCard / 列表项卡片（默认）
 * - radiusXl = 24dp 底部导航顶部圆角 / 弹层 / 大浮层
 */
object Shape {
    val radiusSm = RoundedCornerShape(8.dp)
    val radiusMd = RoundedCornerShape(12.dp)
    val radiusLg = RoundedCornerShape(16.dp)
    val radiusXl = RoundedCornerShape(24.dp)
}

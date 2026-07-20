package com.task.platform.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 品牌主色（权威橙）。浅色与深色模式保持一致，见架构决策 Q1。
 */
val BrandOrange: Color = Color(0xFFFF8C00)

/**
 * 浅色模式 ColorScheme —— 取值依据 PRD §4.1.3。
 */
val lightScheme = lightColorScheme(
    primary = Color(0xFFFF8C00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFF4A2400),
    secondary = Color(0xFFB26A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE8CC),
    onSecondaryContainer = Color(0xFF3D2100),
    tertiary = Color(0xFF2196F3),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFE53935),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF212121),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF616161),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0)
)

/**
 * 深色模式 ColorScheme —— 取值依据 PRD §4.1.4。
 */
val darkScheme = darkColorScheme(
    primary = Color(0xFFFF8C00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5C2E00),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF3D2100),
    secondaryContainer = Color(0xFF4A2E00),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFF64B5F6),
    onTertiary = Color(0xFF00344F),
    error = Color(0xFFEF5350),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF410002),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2A2A2C),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF3A3A3C),
    outlineVariant = Color(0xFF2A2A2C)
)

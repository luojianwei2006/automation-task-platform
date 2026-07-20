package com.task.platform.ui.task

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.task.platform.ui.components.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.task.platform.viewmodel.AutoViewModel
import com.task.platform.ui.theme.Shape
import androidx.compose.material3.MaterialTheme

// ─── 配色 ───────────────────────────────────────
private val Gray50 = Color(0xFFFAFAFA)
private val Gray100 = Color(0xFFF5F5F5)
private val Gray300 = Color(0xFFE0E0E0)
private val Gray500 = Color(0xFF9E9E9E)
private val Gray900 = Color(0xFF212121)
private val GreenSuccess = Color(0xFF4CAF50)
private val RedFail = Color(0xFFE53935)

/**
 * 自动化执行进度底部面板
 *
 * 显示内容：
 * - 当前执行状态（进行中/已完成/失败）
 * - 每步详情（步骤名称 + 成功/失败标记 + 详细信息）
 * - 停止按钮（执行中可用）
 * - 关闭按钮（完成后可用）
 *
 * @param autoUiState 当前自动化 UI 状态
 * @param steps 步骤列表
 * @param onStop 停止回调
 * @param onClose 关闭回调
 */
@Composable
fun AutomationProgressSheet(
    autoUiState: AutoViewModel.AutoUiState,
    steps: List<AutoViewModel.AutoStep>,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Gray50)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ── 标题栏 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "自动化执行中",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Gray900
            )
            when (autoUiState) {
                is AutoViewModel.AutoUiState.Completed -> {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "关闭", tint = Gray500)
                    }
                }
                else -> {
                    OutlinedButton(
                        onClick = onStop,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedFail),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("停止", fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── 当前状态指示器 ──
        when (autoUiState) {
            is AutoViewModel.AutoUiState.Running -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "正在执行: ${autoUiState.currentStep}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            is AutoViewModel.AutoUiState.Completed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (autoUiState.success) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        null,
                        tint = if (autoUiState.success) GreenSuccess else RedFail,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        autoUiState.message,
                        fontSize = 14.sp,
                        color = if (autoUiState.success) GreenSuccess else RedFail,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            is AutoViewModel.AutoUiState.Preparing -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("准备中...", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
            }
            is AutoViewModel.AutoUiState.Error -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null, tint = RedFail, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        (autoUiState as AutoViewModel.AutoUiState.Error).message,
                        fontSize = 14.sp,
                        color = RedFail,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            else -> { }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 步骤列表 ──
        if (steps.isNotEmpty()) {
        Surface(
            shape = Shape.radiusMd,
            color = MaterialTheme.colorScheme.surface
        ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    steps.forEachIndexed { index, step ->
                        AutomationStepItem(
                            step = step,
                            isLast = index == steps.lastIndex
                        )
                    }
                }
            }
        } else {
            // 占位提示
            Box(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("等待开始...", fontSize = 14.sp, color = Gray500)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 单个自动化步骤项
 *
 * 显示步骤序号连线、状态图标（进行中/成功/失败）、步骤描述和详细信息
 */
@Composable
fun AutomationStepItem(
    step: AutoViewModel.AutoStep,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // ── 状态图标 ──
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (step.status) {
                1 -> Icon(
                    Icons.Default.CheckCircle, null,
                    tint = GreenSuccess,
                    modifier = Modifier.size(20.dp)
                )
                2 -> Icon(
                    Icons.Default.Cancel, null,
                    tint = RedFail,
                    modifier = Modifier.size(20.dp)
                )
                else -> LoadingIndicator(
                    modifier = Modifier.size(16.dp),
                    size = 16.dp
                )
            }
        }

        // ── 竖线连接 ──
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(
                            if (step.status == 1) GreenSuccess.copy(alpha = 0.3f)
                            else Gray300
                        )
                )
            }
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Spacer(modifier = Modifier.width(8.dp))

        // ── 步骤信息 ──
        Column(modifier = Modifier.weight(1f)) {
            Text(
                step.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (step.status == 2) RedFail else Gray900
            )
            if (step.detail.isNotEmpty()) {
                Text(
                    step.detail,
                    fontSize = 12.sp,
                    color = Gray500,
                    maxLines = 2
                )
            }
        }
    }
}

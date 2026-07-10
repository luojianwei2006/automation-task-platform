package com.task.platform.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.task.platform.BuildConfig

/**
 * 将字节数格式化为 MB（保留两位小数）。
 */
private fun formatMb(bytes: Long): String = "%.2f MB".format(bytes / 1024f / 1024f)

/**
 * 版本更新弹窗（可选更新）
 *
 * - [UpdateState.Available]：显示「发现新版本」对话框，含「立即更新」与「稍后再说」；
 * - [UpdateState.Downloading]：显示下载中进度（不可取消，系统安装器将自动弹出）；
 * - 其它状态：不渲染（Error 仅日志 / Toast 兜底，不阻塞用户）。
 *
 * @param state       当前更新状态
 * @param onUpdate    点击「立即更新」
 * @param onDismiss   点击「稍后再说」或点击对话框外部 / 返回键
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is UpdateState.Available -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(text = "发现新版本", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Text(
                        text = "检测到新版本 v${state.version}，是否立即更新？\n当前版本：v${BuildConfig.VERSION_NAME}",
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = onUpdate) {
                        Text(text = "立即更新", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(text = "稍后再说")
                    }
                }
            )
        }

        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(text = "正在下载更新", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Column {
                        Text(
                            text = if (state.totalBytes > 0) {
                                "已下载 ${formatMb(state.downloadedBytes)} / ${formatMb(state.totalBytes)} (${state.percent}%)"
                            } else {
                                "已下载 ${formatMb(state.downloadedBytes)}（总大小获取中…）"
                            },
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (state.totalBytes > 0) {
                            LinearProgressIndicator(
                                progress = { state.percent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {}
            )
        }

        else -> {
            // Idle / Checking / Error：不展示全局弹窗
        }
    }
}

package com.task.platform.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController

/**
 * 基础屏幕组件 - 统一处理加载和错误状态
 * 
 * 使用方式：
 *   BaseScreen(
 *       viewModel = myViewModel,
 *       navController = navController
 *   ) {
 *       // 屏幕内容
 *   }
 */
@Composable
fun <T : BaseViewModel> BaseScreen(
    viewModel: T,
    navController: NavController? = null,
    content: @Composable () -> Unit
) {
    // 加载指示器
    if (viewModel.isLoading.collectAsState().value) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    // 内容区域
    content()

    // 错误对话框
    val errorMessage = viewModel.errorMessage.collectAsState().value
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            title = { Text(text = "提示") },
            text = { Text(text = errorMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearErrorMessage() }) {
                    Text(text = "确定")
                }
            }
        )
    }
}

package com.task.platform.ui.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.task.platform.R
import com.task.platform.update.UpdateViewModel
import com.task.platform.viewmodel.ProfileViewModel

/**
 * 设置页面
 * 修改密码 + 消息通知开关
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val error by viewModel.errorMessage.collectAsState()

    var showChangePwDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val errorText = error
    LaunchedEffect(errorText) {
        if (errorText != null) {
            android.widget.Toast.makeText(context, errorText, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color(0xFFFF8C00))
            }
            Text(
                text = "设置",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 修改密码
        SettingsCard(title = "修改密码") {
            Button(
                onClick = { showChangePwDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFF8C00))
            ) {
                Text("修改密码", color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 消息通知
        SettingsCard(title = "消息通知") {
            var enabled by remember { mutableStateOf(true) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("接收通知", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 关于我们（直接走公开读接口 agreement/about，从后台读取协议内容）
        SettingsCard(title = "关于") {
            Button(
                onClick = { navController.navigate("agreement/about") },
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text("关于我们")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 隐私协议
        SettingsCard(title = "隐私协议") {
            Button(
                onClick = { navController.navigate("agreement/privacy") },
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text("查看隐私协议")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 注册协议
        SettingsCard(title = "注册协议") {
            Button(
                onClick = { navController.navigate("agreement/register") },
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text("查看注册协议")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 检查新版本
        SettingsCard(title = "版本更新") {
            Button(
                onClick = {
                    Toast.makeText(context, "正在检查…", Toast.LENGTH_SHORT).show()
                    updateViewModel.manualCheck(context)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8C00))
            ) {
                Text("检查新版本", color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 退出登录
        Surface(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Box(contentAlignment = Alignment.Center) {
                TextButton(
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("退出登录", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // 修改密码弹窗
    if (showChangePwDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePwDialog = false },
            onChangePassword = { oldPw, newPw, confirmPw ->
                viewModel.changePassword(oldPw, newPw, confirmPw) { success, msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    if (success) showChangePwDialog = false
                }
            }
        )
    }

    // 退出登录确认
    if (showLogoutConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("确认退出登录") },
            text = { Text("退出后需要重新登录") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("退出", color = MaterialTheme.colorScheme.onError) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            }
        )
    }
}

// ==================== 设置卡片 ====================

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

// ==================== 修改密码弹窗 ====================

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onChangePassword: (old: String, new: String, confirm: String) -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("旧密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("新密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("确认新密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onChangePassword(oldPassword, newPassword, confirmPassword) },
                enabled = oldPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank()
            ) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

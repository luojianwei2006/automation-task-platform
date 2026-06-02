package com.task.platform.ui.earnings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.task.platform.viewmodel.ProfileViewModel
import com.task.platform.viewmodel.WithdrawViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithdrawScreen(
    navController: NavController,
    withdrawVM: WithdrawViewModel = hiltViewModel(),
    profileVM: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userInfo by profileVM.userInfo.collectAsState()
    val balance by profileVM.balance.collectAsState()
    val isLoading by withdrawVM.isLoading.collectAsState()
    val applySuccess by withdrawVM.applySuccess.collectAsState()

    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("wechat") }
    var account by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { profileVM.loadProfile() }

    LaunchedEffect(applySuccess) {
        if (applySuccess) {
            withdrawVM.resetApplySuccess()
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("提现", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) { Text("取消") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // 可提现余额
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("可提现余额", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("¥${balance?.let { String.format("%.2f", it) } ?: "0.00"}",
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            // 金额输入
            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it },
                label = { Text("提现金额") },
                placeholder = { Text("请输入提现金额") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 方式选择
            Text("提现方式", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Row {
                FilterChip(selected = method == "wechat", onClick = { method = "wechat" }, label = { Text("微信") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = method == "alipay", onClick = { method = "alipay" }, label = { Text("支付宝") }, modifier = Modifier.weight(1f))
            }

            // 账号输入
            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text(if (method == "wechat") "微信号" else "支付宝账号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.weight(1f))

            // 提交
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull()
                    if (amt == null || amt <= 0) {
                        android.widget.Toast.makeText(context, "请输入有效金额", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (account.isBlank()) {
                        android.widget.Toast.makeText(context, "请输入收款账号", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        withdrawVM.applyWithdraw(amt, method, account) { success, msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading && amount.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("提交申请", fontSize = 16.sp)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

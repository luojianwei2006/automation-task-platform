package com.task.platform.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.task.platform.viewmodel.RealAuthViewModel

/**
 * 实名认证页面
 *
 * 状态流：
 * - 未认证 → 展示提交表单
 * - 审核中 → 展示等待提示
 * - 已认证 → 展示认证成功
 * - 认证失败 → 展示失败原因 + 重新提交
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealAuthScreen(
    onBack: () -> Unit,
    viewModel: RealAuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState) {
        if (uiState is RealAuthViewModel.UiState.Error) {
            snackbarHostState.showSnackbar((uiState as RealAuthViewModel.UiState.Error).message)
            viewModel.resetError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("实名认证") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val state = uiState) {
                is RealAuthViewModel.UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is RealAuthViewModel.UiState.Passed -> {
                    AuthPassedContent()
                }

                is RealAuthViewModel.UiState.Pending -> {
                    AuthPendingContent()
                }

                is RealAuthViewModel.UiState.Form -> {
                    AuthFormContent(
                        failReason = state.failReason,
                        viewModel = viewModel
                    )
                }

                else -> {}
            }
        }
    }
}

// ==================== 认证通过 ====================
@Composable
private fun AuthPassedContent() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FFF0))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF52C41A),
                modifier = Modifier.size(56.dp)
            )
            Text("实名认证已通过", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("您的账号已完成实名认证，可以申请提现", color = Color.Gray)
        }
    }
}

// ==================== 审核中 ====================
@Composable
private fun AuthPendingContent() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E6))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Pending,
                contentDescription = null,
                tint = Color(0xFFFA8C16),
                modifier = Modifier.size(56.dp)
            )
            Text("认证审核中", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("预计1-2个工作日内完成审核", color = Color.Gray)
            Text("审核通过后将收到站内消息通知", color = Color.Gray, fontSize = 13.sp)
        }
    }
}

// ==================== 提交表单 ====================
@Composable
private fun AuthFormContent(
    failReason: String?,
    viewModel: RealAuthViewModel
) {
    var realName by remember { mutableStateOf("") }
    var idCard by remember { mutableStateOf("") }
    // 在真实实现中，照片上传需集成 COS SDK 先上传获取URL
    var idCardFrontUrl by remember { mutableStateOf("") }
    var idCardBackUrl by remember { mutableStateOf("") }

    val isSubmitting by viewModel.isSubmitting.collectAsState()

    // 失败原因提示
    if (failReason != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F0))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Cancel, null, tint = Color.Red)
                Column {
                    Text("上次认证失败", fontWeight = FontWeight.Medium, color = Color.Red)
                    Text(failReason, fontSize = 13.sp, color = Color.Gray)
                }
            }
        }
    }

    // 说明卡片
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F5FF))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.HowToReg, null, tint = MaterialTheme.colorScheme.primary)
                Text("为什么需要实名认证？", fontWeight = FontWeight.Medium)
            }
            Text("• 确保提现账户安全，防止资金风险", fontSize = 13.sp, color = Color.Gray)
            Text("• 完成认证可享受更高提现额度", fontSize = 13.sp, color = Color.Gray)
            Text("• 您的身份信息经加密存储，严格保密", fontSize = 13.sp, color = Color.Gray)
        }
    }

    // 表单
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("填写认证信息", fontSize = 16.sp, fontWeight = FontWeight.Medium)

            OutlinedTextField(
                value = realName,
                onValueChange = { realName = it },
                label = { Text("真实姓名 *") },
                placeholder = { Text("请输入真实姓名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = idCard,
                onValueChange = { if (it.length <= 18) idCard = it },
                label = { Text("身份证号 *") },
                placeholder = { Text("18位身份证号码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Divider()

            // 照片上传区域（简化实现，实际需要拍照/相册选取 + COS上传）
            Text("上传证件照片", fontSize = 14.sp, fontWeight = FontWeight.Medium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PhotoUploadBox(
                    label = "身份证正面",
                    photoUrl = idCardFrontUrl,
                    modifier = Modifier.weight(1f),
                    onUploadClick = {
                        // TODO: 调起相机/相册，上传到COS后获取URL
                        idCardFrontUrl = "https://placeholder.url/front.jpg"
                    }
                )
                PhotoUploadBox(
                    label = "身份证背面",
                    photoUrl = idCardBackUrl,
                    modifier = Modifier.weight(1f),
                    onUploadClick = {
                        idCardBackUrl = "https://placeholder.url/back.jpg"
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.submitRealAuth(realName, idCard, idCardFrontUrl, idCardBackUrl)
                },
                enabled = !isSubmitting && realName.isNotBlank() && idCard.length == 18
                        && idCardFrontUrl.isNotBlank() && idCardBackUrl.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("提交认证", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PhotoUploadBox(
    label: String,
    photoUrl: String,
    modifier: Modifier = Modifier,
    onUploadClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(120.dp),
        shape = RoundedCornerShape(8.dp),
        onClick = onUploadClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            if (photoUrl.isBlank()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+", fontSize = 28.sp, color = Color.Gray)
                    Text(label, fontSize = 12.sp, color = Color.Gray)
                }
            } else {
                // TODO: 使用 Coil 加载照片
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF52C41A))
                    Text(label, fontSize = 12.sp)
                }
            }
        }
    }
}

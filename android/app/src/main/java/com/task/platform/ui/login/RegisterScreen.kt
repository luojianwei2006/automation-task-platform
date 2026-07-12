package com.task.platform.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusDirection
import androidx.hilt.navigation.compose.hiltViewModel
import com.task.platform.viewmodel.LoginViewModel

// 配色
private val RegOrange = Color(0xFFFF8C00)
private val RegOrangeLight = Color(0xFFFFB347)
private val Gray100 = Color(0xFFF5F5F5)
private val Gray300 = Color(0xFFE0E0E0)
private val Gray500 = Color(0xFF9E9E9E)
private val Gray700 = Color(0xFF616161)

/**
 * 注册页面
 * 橙色顶部渐变 + 步骤指示 + 白色卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val countdown by viewModel.countdown.collectAsState()
    // 全局开关：是否需要短信验证码（关闭时隐藏注册页验证码输入框、放宽校验）
    val requirePhoneVerify by viewModel.requirePhoneVerify.collectAsState()

    var phone by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState) {
        if (uiState is LoginViewModel.UiState.Success) {
            onRegisterSuccess()
            viewModel.resetUiState()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState) {
        if (uiState is LoginViewModel.UiState.Error) {
            snackbarHostState.showSnackbar((uiState as LoginViewModel.UiState.Error).message)
            viewModel.resetError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = RegOrange
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ===== 顶部渐变区 =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .offset(y = (-16).dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(RegOrange, RegOrangeLight)
                        ),
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = 16.dp)
                ) {
                    Text(
                        text = "创建新账号",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "填写信息，开始赚取奖励",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )

                    // 步骤指示点
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == 0) 24.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == 0) Color.White
                                        else Color.White.copy(alpha = 0.4f)
                                    )
                            )
                        }
                    }
                }
            }

            // ===== 注册卡片 =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-40).dp)
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 分段标题：基本信息
                    SectionLabel(icon = Icons.Default.Person, text = "基本信息")

                    // 手机号
                    ModernInputField(
                        value = phone,
                        onValueChange = { if (it.length <= 11) phone = it },
                        label = "手机号",
                        icon = Icons.Default.Phone,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    // 验证码（仅当全局开关要求短信验证时才渲染）
                    if (requirePhoneVerify) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = smsCode,
                                onValueChange = { if (it.length <= 6) smsCode = it },
                                label = { Text("短信验证码", fontSize = 13.sp) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                ),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RegOrange,
                                    unfocusedBorderColor = Gray300,
                                    focusedLabelColor = RegOrange,
                                    cursorColor = RegOrange
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = { viewModel.sendSmsCode(phone, type = 1) },
                                enabled = countdown == 0 && uiState !is LoginViewModel.UiState.Loading,
                                modifier = Modifier.height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (countdown > 0) Gray300 else RegOrange,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = if (countdown > 0) "${countdown}s" else "获取验证码",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 分段标题：安全设置
                    SectionLabel(icon = Icons.Default.Lock, text = "安全设置")

                    // 密码
                    ModernPasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = "设置密码（6位以上）",
                        passwordVisible = passwordVisible,
                        onToggleVisible = { passwordVisible = !passwordVisible },
                        imeAction = ImeAction.Next,
                        onDone = { focusManager.moveFocus(FocusDirection.Down) }
                    )

                    // 确认密码
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("确认密码", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, null, tint = RegOrange, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { confirmVisible = !confirmVisible }) {
                                Icon(
                                    imageVector = if (confirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = Gray500,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                        supportingText = {
                            if (confirmPassword.isNotEmpty() && confirmPassword != password) {
                                Text("两次密码不一致", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RegOrange,
                            unfocusedBorderColor = Gray300,
                            focusedLabelColor = RegOrange,
                            cursorColor = RegOrange,
                            errorBorderColor = MaterialTheme.colorScheme.error
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 分段标题：个人资料
                    SectionLabel(icon = Icons.Default.Badge, text = "个人资料")

                    // 昵称
                    ModernInputField(
                        value = nickname,
                        onValueChange = { if (it.length <= 16) nickname = it },
                        label = "昵称",
                        icon = Icons.Default.Person,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    // 邀请码
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { if (it.length <= 8) inviteCode = it },
                        label = { Text("邀请码（可选）", fontSize = 13.sp) },
                        placeholder = { Text("填写邀请码享受额外奖励", fontSize = 12.sp, color = Gray500) },
                        leadingIcon = {
                            Icon(Icons.Default.CardGiftcard, null, tint = RegOrange, modifier = Modifier.size(20.dp))
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RegOrange,
                            unfocusedBorderColor = Gray300,
                            focusedLabelColor = RegOrange,
                            cursorColor = RegOrange
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 注册按钮
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.register(
                                phone, smsCode, password, confirmPassword,
                                nickname, inviteCode.ifBlank { null }
                            )
                        },
                        enabled = uiState !is LoginViewModel.UiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RegOrange,
                            disabledContainerColor = RegOrange.copy(alpha = 0.5f)
                        )
                    ) {
                        if (uiState is LoginViewModel.UiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                "注 册",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 用户协议
            Text(
                text = "注册即表示您同意《用户协议》和《隐私政策》",
                color = Gray500,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // 返回登录
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已有账号？",
                    color = Gray500,
                    fontSize = 13.sp
                )
                TextButton(onClick = onNavigateBack) {
                    Text(
                        text = "返回登录",
                        color = RegOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** 分段小标题 */
@Composable
private fun SectionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = RegOrange)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = RegOrange
        )
    }
}

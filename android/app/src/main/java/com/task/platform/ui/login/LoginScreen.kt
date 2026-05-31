package com.task.platform.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
private val LoginOrange = Color(0xFFFF8C00)
private val LoginOrangeLight = Color(0xFFFFB347)
private val LoginOrangeBg = Color(0xFFFFF8F0)
private val Gray50 = Color(0xFFFAFAFA)
private val Gray100 = Color(0xFFF5F5F5)
private val Gray300 = Color(0xFFE0E0E0)
private val Gray500 = Color(0xFF9E9E9E)
private val Gray700 = Color(0xFF616161)
private val Gray900 = Color(0xFF212121)

/**
 * 登录页面
 * 橙色顶部渐变 + 白色圆角卡片 + 现代输入框风格
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val countdown by viewModel.countdown.collectAsState()
    val savedPhone by viewModel.savedPhone.collectAsState()
    val savedPassword by viewModel.savedPassword.collectAsState()

    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSmsMode by remember { mutableStateOf(false) }
    var credentialsLoaded by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    // 自动填充保存的账号密码
    LaunchedEffect(savedPhone, savedPassword) {
        if (!credentialsLoaded) {
            if (!savedPhone.isNullOrBlank()) phone = savedPhone!!
            if (!savedPassword.isNullOrBlank()) {
                password = savedPassword!!
                isSmsMode = false  // 有保存的密码则默认密码登录模式
            }
            credentialsLoaded = true
        }
    }

    // 监听登录成功
    LaunchedEffect(uiState) {
        if (uiState is LoginViewModel.UiState.Success) {
            onLoginSuccess()
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
        containerColor = Color.White
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // ===== 顶部渐变区域 =====
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(LoginOrange, LoginOrangeLight)
                            ),
                            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Logo 圆形容器
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.TaskAlt,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "欢迎回来",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "登录账号，继续赚取奖励",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp
                        )
                    }
                }

                // ===== 登录卡片（向上偏移叠加在渐变区域上）=====
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
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Tab 切换
                        LoginTabBar(
                            isSmsMode = isSmsMode,
                            onModeChange = { isSmsMode = it }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 手机号输入框
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

                        Spacer(modifier = Modifier.height(14.dp))

                        // 密码模式
                        AnimatedVisibility(visible = !isSmsMode, enter = fadeIn(), exit = fadeOut()) {
                            ModernPasswordField(
                                value = password,
                                onValueChange = { password = it },
                                label = "密码",
                                passwordVisible = passwordVisible,
                                onToggleVisible = { passwordVisible = !passwordVisible },
                                imeAction = ImeAction.Done,
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.loginWithPassword(phone, password)
                                }
                            )
                        }

                        // 验证码模式
                        AnimatedVisibility(visible = isSmsMode, enter = fadeIn(), exit = fadeOut()) {
                            SmsCodeField(
                                code = smsCode,
                                onCodeChange = { if (it.length <= 6) smsCode = it },
                                countdown = countdown,
                                onSendCode = { viewModel.sendSmsCode(phone, type = 2) },
                                enabled = uiState !is LoginViewModel.UiState.Loading
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 登录按钮
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (isSmsMode) {
                                    viewModel.loginWithSms(phone, smsCode)
                                } else {
                                    viewModel.loginWithPassword(phone, password)
                                }
                            },
                            enabled = uiState !is LoginViewModel.UiState.Loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LoginOrange,
                                disabledContainerColor = LoginOrange.copy(alpha = 0.5f)
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
                                    "登 录",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 4.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 注册链接
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("还没有账号？", fontSize = 14.sp, color = Gray500)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "立即注册",
                                fontSize = 14.sp,
                                color = LoginOrange,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onNavigateToRegister() }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 用户协议
                Text(
                    text = "登录即表示您同意《用户协议》和《隐私政策》",
                    color = Gray500,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// ==================== 组件 ====================

/** 登录模式 Tab 栏 */
@Composable
fun LoginTabBar(
    isSmsMode: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gray100)
            .padding(4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        LoginTabItem(
            text = "密码登录",
            selected = !isSmsMode,
            onClick = { onModeChange(false) },
            modifier = Modifier.weight(1f)
        )
        LoginTabItem(
            text = "验证码登录",
            selected = isSmsMode,
            onClick = { onModeChange(true) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun LoginTabItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) LoginOrange else Gray500,
            fontSize = 14.sp
        )
    }
}

/** 现代风格输入框 */
@Composable
fun ModernInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = LoginOrange, modifier = Modifier.size(20.dp))
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LoginOrange,
            unfocusedBorderColor = Gray300,
            focusedLabelColor = LoginOrange,
            cursorColor = LoginOrange
        )
    )
}

/** 密码输入框 */
@Composable
fun ModernPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    passwordVisible: Boolean,
    onToggleVisible: () -> Unit,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = LoginOrange, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = Gray500,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LoginOrange,
            unfocusedBorderColor = Gray300,
            focusedLabelColor = LoginOrange,
            cursorColor = LoginOrange
        )
    )
}

/** 验证码输入行 */
@Composable
fun SmsCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    countdown: Int,
    onSendCode: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("验证码", fontSize = 13.sp) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LoginOrange,
                unfocusedBorderColor = Gray300,
                focusedLabelColor = LoginOrange,
                cursorColor = LoginOrange
            )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Button(
            onClick = onSendCode,
            enabled = countdown == 0 && enabled,
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (countdown > 0) Gray300 else LoginOrange,
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

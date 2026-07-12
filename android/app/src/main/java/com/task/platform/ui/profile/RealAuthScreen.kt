package com.task.platform.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.task.platform.viewmodel.RealAuthViewModel
import java.util.Locale

// ─── 配色 ─────────────────────────────────────
private val Orange = Color(0xFFFF8C00)
private val OrangeLight = Color(0xFFFFF0E0)
private val OrangeBg = Color(0xFFFFF8F0)
private val Gray50 = Color(0xFFFAFAFA)
private val Gray100 = Color(0xFFF5F5F5)
private val Gray300 = Color(0xFFE0E0E0)
private val Gray400 = Color(0xFFBDBDBD)
private val Gray500 = Color(0xFF9E9E9E)
private val Gray700 = Color(0xFF616161)
private val Gray900 = Color(0xFF212121)
private val Green = Color(0xFF4CAF50)
private val GreenLight = Color(0xFFE8F5E9)
private val Amber = Color(0xFFFF9800)
private val AmberLight = Color(0xFFFFF3E0)
private val Red = Color(0xFFE53935)
private val RedLight = Color(0xFFFFEBEE)

// ==================== 主入口 ====================

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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("实名认证", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = Orange)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Orange,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Gray50
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            when (val state = uiState) {
                is RealAuthViewModel.UiState.Loading -> {
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Orange)
                    }
                }
                is RealAuthViewModel.UiState.Passed -> PassedContent(onBack = onBack)
                is RealAuthViewModel.UiState.Pending -> PendingContent(onBack = onBack)
                is RealAuthViewModel.UiState.Form -> FormContent(
                    failReason = state.failReason,
                    viewModel = viewModel
                )
                else -> {}
            }
        }
    }
}

// ==================== 已认证 ====================

@Composable
private fun PassedContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        // 成功图标
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(GreenLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = Green,
                modifier = Modifier.size(52.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text("实名认证已通过", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gray900)
        Spacer(Modifier.height(8.dp))
        Text(
            "您的账号已完成实名认证，可以正常使用全部功能",
            fontSize = 15.sp,
            color = Gray500,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(32.dp))

        // 权益卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = GreenLight.copy(alpha = 0.3f))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BenefitRow(Icons.Default.Savings, "可申请提现，单笔最高 ¥5000")
                BenefitRow(Icons.Default.VerifiedUser, "身份已核验，交易更安全")
                BenefitRow(Icons.Default.Security, "享受平台安全保障服务")
            }
        }

        Spacer(Modifier.height(32.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange)
        ) {
            Text("返回", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BenefitRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Green, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, color = Gray700)
    }
}

// ==================== 审核中 ====================

@Composable
private fun PendingContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(AmberLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.HourglassTop,
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(52.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text("认证审核中", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gray900)
        Spacer(Modifier.height(12.dp))
        Text(
            "您的实名认证申请已提交，预计1-2个工作日内完成审核",
            fontSize = 15.sp,
            color = Gray500,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "审核通过后将收到站内消息通知",
            fontSize = 13.sp,
            color = Gray400,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        // 审核进度条（模拟）
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("审核进度", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Gray900)

                // 步骤 1：已提交 ✓
                StepRow(icon = Icons.Default.CheckCircle, text = "资料已提交", active = true, color = Green)
                StepConnector()
                // 步骤 2：审核中 ○
                StepRow(icon = Icons.Default.HourglassEmpty, text = "系统审核中", active = true, color = Amber)
                StepConnector()
                // 步骤 3：待完成 ○
                StepRow(icon = Icons.Default.FiberManualRecord, text = "审核完成", active = false, color = Gray300)

                Spacer(Modifier.height(4.dp))

                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Amber,
                    trackColor = Gray100
                )
                Spacer(Modifier.height(4.dp))
                Text("预计剩余时间：1-2 个工作日", fontSize = 12.sp, color = Gray400)
            }
        }

        Spacer(Modifier.height(32.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange)
        ) {
            Text("返回", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StepRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    active: Boolean,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            fontSize = 14.sp,
            color = if (active) Gray900 else Gray400,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun StepConnector() {
    Box(
        modifier = Modifier
            .padding(start = 9.dp)
            .width(2.dp)
            .height(20.dp)
            .background(Gray100)
    )
}

// ==================== 提交表单 ====================

@Composable
private fun FormContent(
    failReason: String?,
    viewModel: RealAuthViewModel
) {
    var realName by remember { mutableStateOf("") }
    var idCard by remember { mutableStateOf("") }
    var idCardFrontUri by remember { mutableStateOf<Uri?>(null) }
    var idCardBackUri by remember { mutableStateOf<Uri?>(null) }
    var idCardFrontUrl by remember { mutableStateOf("") }
    var idCardBackUrl by remember { mutableStateOf("") }
    var uploadingFront by remember { mutableStateOf(false) }
    var uploadingBack by remember { mutableStateOf(false) }

    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val context = LocalContext.current

    // 身份证号格式实时校验
    val idCardError = remember(idCard) {
        if (idCard.isEmpty()) null
        else if (!idCard.matches(Regex("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$")))
            "身份证号格式不正确"
        else null
    }

    // 图片选择器
    val frontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            idCardFrontUri = it
            uploadingFront = true
            viewModel.uploadIdCardImage(it) { success, url ->
                uploadingFront = false
                if (success) {
                    idCardFrontUrl = url
                } else {
                    // 上传失败，uri 仍可本地预览但不提交
                    idCardFrontUrl = ""
                }
            }
        }
    }

    val backLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            idCardBackUri = it
            uploadingBack = true
            viewModel.uploadIdCardImage(it) { success, url ->
                uploadingBack = false
                if (success) {
                    idCardBackUrl = url
                } else {
                    // 上传失败，uri 仍可本地预览但不提交
                    idCardBackUrl = ""
                }
            }
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 失败原因提示 ──
        if (failReason != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = RedLight)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.GppBad,
                        contentDescription = null,
                        tint = Red,
                        modifier = Modifier.size(22.dp).padding(top = 1.dp)
                    )
                    Column {
                        Text(
                            "上次认证未通过，请修改后重新提交",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Red
                        )
                        Text(failReason, fontSize = 13.sp, color = Gray700)
                    }
                }
            }
        }

        // ── 为什么需要实名认证 ──
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = OrangeBg)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = Orange,
                    modifier = Modifier.size(22.dp).padding(top = 1.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("为什么需要认证？", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
                    Text("• 确保提现账户安全，防范资金风险", fontSize = 12.sp, color = Gray700)
                    Text("• 信息经加密存储，严格保密", fontSize = 12.sp, color = Gray700)
                }
            }
        }

        // ── 表单卡片 ──
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("填写身份信息", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Gray900)

                // 姓名
                OutlinedTextField(
                    value = realName,
                    onValueChange = { if (it.length <= 64) realName = it },
                    label = { Text("真实姓名") },
                    placeholder = { Text("请输入您的真实姓名", color = Gray400) },
                    leadingIcon = { Icon(Icons.Default.Badge, null, tint = Orange) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange,
                        focusedLabelColor = Orange,
                        cursorColor = Orange
                    )
                )

                // 身份证号
                OutlinedTextField(
                    value = idCard,
                    onValueChange = {
                        // 只允许数字和X
                        val filtered = it.filter { c -> c.isDigit() || c.uppercaseChar() == 'X' }
                        if (filtered.length <= 18) idCard = filtered.uppercase()
                    },
                    label = { Text("身份证号码") },
                    placeholder = { Text("18位身份证号码", color = Gray400) },
                    leadingIcon = { Icon(Icons.Default.CreditCard, null, tint = Orange) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = idCardError != null,
                    supportingText = idCardError?.let { { Text(it, color = Red, fontSize = 12.sp) } },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange,
                        focusedLabelColor = Orange,
                        cursorColor = Orange
                    )
                )

                HorizontalDivider(color = Gray100, thickness = 1.dp)

                // 证件照片
                Text("上传证件照片", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Gray900)
                Text(
                    "请确保照片清晰、四角完整、无反光",
                    fontSize = 12.sp,
                    color = Gray400
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PhotoBox(
                        label = "身份证正面",
                        uri = idCardFrontUri,
                        uploading = uploadingFront,
                        modifier = Modifier.weight(1f),
                        onClick = { frontLauncher.launch("image/*") }
                    )
                    PhotoBox(
                        label = "身份证背面",
                        uri = idCardBackUri,
                        uploading = uploadingBack,
                        modifier = Modifier.weight(1f),
                        onClick = { backLauncher.launch("image/*") }
                    )
                }

                Spacer(Modifier.height(4.dp))

                // 提交按钮
                val canSubmit = realName.isNotBlank()
                        && idCardError == null
                        && idCard.length == 18
                        && idCardFrontUrl.isNotBlank()
                        && idCardBackUrl.isNotBlank()
                        && !uploadingFront
                        && !uploadingBack

                Button(
                    onClick = {
                        viewModel.submitRealAuth(realName, idCard, idCardFrontUrl, idCardBackUrl)
                    },
                    enabled = canSubmit && !isSubmitting,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Orange,
                        disabledContainerColor = Gray300
                    )
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("提交中...", fontSize = 16.sp)
                    } else {
                        Icon(Icons.Default.VerifiedUser, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("提交认证", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

// ==================== 照片上传框 ====================

@Composable
private fun PhotoBox(
    label: String,
    uri: Uri?,
    uploading: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        enabled = !uploading,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = if (uri == null)
            androidx.compose.foundation.BorderStroke(1.5.dp, Gray300)
        else
            androidx.compose.foundation.BorderStroke(1.5.dp, Green)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (uploading) {
                // 上传中
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Orange,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("上传中...", fontSize = 12.sp, color = Orange)
                }
            } else if (uri == null) {
                // 未选择
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = Gray400,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(label, fontSize = 13.sp, color = Gray500, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(2.dp))
                    Text("点击上传", fontSize = 11.sp, color = Gray400)
                }
            } else {
                // 已选择，显示预览
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // 覆盖层：已上传标识
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 12.sp, color = Color.White)
                        Text("点击重新上传", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

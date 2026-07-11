package com.task.platform.ui.earnings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import java.util.Locale
import com.task.platform.navigation.TaskRoutes
import com.task.platform.viewmodel.ProfileViewModel
import com.task.platform.viewmodel.WithdrawViewModel
import kotlinx.coroutines.launch

/** 收款方式品牌点缀色（仅用于视觉，不影响逻辑）。 */
private val WeChatGreen = Color(0xFF07C160)
private val AlipayBlue = Color(0xFF1677FF)
private val Gray900 = Color(0xFF212121)

private data class PaymentOption(
    val method: String,
    val title: String,
    val account: String?,
    val qrcodeUrl: String?
)

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

    // 已绑定的收款账号（来自 userInfo）
    val boundMethods = remember(userInfo) {
        buildList {
            if (!userInfo?.wechatQrcode.isNullOrBlank())
                add(PaymentOption("wechat", "微信收款码", userInfo?.wechatAccount, userInfo?.wechatQrcode))
            if (!userInfo?.alipayQrcode.isNullOrBlank())
                add(PaymentOption("alipay", "支付宝收款码", userInfo?.alipayAccount, userInfo?.alipayQrcode))
        }
    }
    var selected by remember { mutableStateOf<PaymentOption?>(null) }

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
                title = { Text("申请提现", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Gray900)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // 可提现余额 —— Hero 渐变大卡片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 26.dp)
            ) {
                Column {
                    Text(
                        "可提现余额",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            "¥",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            balance?.let { String.format(Locale.US, "%.2f", it) } ?: "0.00",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // 金额输入区
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "提现金额",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 大字号金额框
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "¥",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(12.dp))
                        TextField(
                            value = amount,
                            onValueChange = { if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it },
                            placeholder = {
                                Text(
                                    "0.00",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            textStyle = TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.outline,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }

                // 快速金额 chips
                val quickAmounts = listOf("100", "200", "500")
                val allAmount = balance?.let { String.format(Locale.US, "%.2f", it) } ?: "0.00"
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    quickAmounts.forEach { value ->
                        FilterChip(
                            selected = amount == value,
                            onClick = { amount = value },
                            label = { Text("¥$value", fontWeight = FontWeight.Medium) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    FilterChip(
                        selected = balance != null && amount == allAmount,
                        onClick = { if (balance != null) amount = allAmount },
                        label = { Text("全部", fontWeight = FontWeight.Medium) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // 收款账号选择
            Text(
                "收款账号",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (boundMethods.isEmpty()) {
                // 未绑定任何收款账号：展示添加入口
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { navController.navigate(TaskRoutes.WALLET_BINDING) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("添加收款账号", fontWeight = FontWeight.Medium)
                            Text(
                                "你还没有绑定收款账号，点击去绑定微信/支付宝收款码",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                // 已绑定：逐条展示收款码，供选择（单选卡片 + RadioButton 选中态）
                val current = selected ?: boundMethods.first()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    boundMethods.forEach { opt ->
                        val isSel = opt.method == current.method
                        val accent = if (opt.method == "wechat") WeChatGreen else AlipayBlue
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { selected = opt },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isSel) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 带品牌色底板的收款码缩略图
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = opt.qrcodeUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(opt.title, fontWeight = FontWeight.Medium)
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier.size(7.dp).background(accent, CircleShape)
                                        )
                                    }
                                    if (!opt.account.isNullOrBlank())
                                        Text(
                                            opt.account ?: "",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                }
                                RadioButton(
                                    selected = isSel,
                                    onClick = { selected = opt },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // 提交
            val chosen = selected ?: boundMethods.firstOrNull()
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull()
                    if (amt == null || amt <= 0) {
                        android.widget.Toast.makeText(context, "请输入有效金额", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (chosen == null) {
                        android.widget.Toast.makeText(context, "请先添加收款账号", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (chosen.account.isNullOrBlank()) {
                        android.widget.Toast.makeText(context, "收款账号信息不完整，请重新绑定", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val method = chosen.method
                    val account = chosen.account ?: ""
                    scope.launch {
                        withdrawVM.applyWithdraw(amt, method, account) { success, msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = !isLoading && amount.isNotBlank() && chosen != null,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isLoading) CircularProgressIndicator(
                    Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                else Text("提交申请", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            // 底部提示（纯展示，不影响逻辑）
            Text(
                "预计到账 1-3 个工作日，审核通过后线下打款",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

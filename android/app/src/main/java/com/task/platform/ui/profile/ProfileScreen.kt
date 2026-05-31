package com.task.platform.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.task.platform.model.UserInfo
import com.task.platform.navigation.TaskRoutes
import com.task.platform.viewmodel.ProfileViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

// ─── 配色 ─────────────────────────────────────
private val HallOrange = Color(0xFFFF8C00)
private val HallOrangeLight = Color(0xFFFFB347)
private val HallOrangeBg = Color(0xFFFFF8F0)
private val Gray50 = Color(0xFFFAFAFA)
private val Gray100 = Color(0xFFF5F5F5)
private val Gray300 = Color(0xFFE0E0E0)
private val Gray500 = Color(0xFF9E9E9E)
private val Gray700 = Color(0xFF616161)
private val Gray900 = Color(0xFF212121)

private val AuthVerifiedGreen = Color(0xFF4CAF50)
private val AuthUnverifiedYellow = Color(0xFFFF9800)
private val AuthPendingBlue = Color(0xFF42A5F5)
private val AuthFailedRed = Color(0xFFE53935)
private val LogoutRed = Color(0xFFE53935)

/**
 * 个人中心屏幕
 */
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val userInfo by viewModel.userInfo.collectAsState()
    val realAuthStatus by viewModel.realAuthStatus.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val logoutSuccess by viewModel.logoutSuccess.collectAsState()
    val showInviteDialog by viewModel.showInviteDialog.collectAsState()
    val inviteCode by viewModel.inviteCode.collectAsState()
    val inviteUrl by viewModel.inviteUrl.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    LaunchedEffect(logoutSuccess) {
        if (logoutSuccess) {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
            viewModel.resetLogoutSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray50)
    ) {
        // ===== 顶部渐变头部 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(HallOrange, HallOrangeLight)
                    ),
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!userInfo?.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = userInfo!!.avatarUrl,
                            contentDescription = "头像",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "默认头像",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.clickable { navController.navigate(TaskRoutes.EDIT_PROFILE) }
                ) {
                    Text(
                        text = userInfo?.nickname ?: "用户",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = maskPhone(userInfo?.phone ?: ""),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AuthStatusBadge(status = realAuthStatus)

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (balance != null)
                                "余额: ¥${String.format("%.2f", balance)}"
                            else
                                "余额: --",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        // ===== 菜单列表 =====
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                MenuGroupCard {
                    MenuItemRow(
                        icon = MenuIcon.Task,
                        title = "我的任务",
                        onClick = { navController.navigate("my_tasks") }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = MenuIcon.Earnings,
                        title = "我的收益",
                        onClick = { navController.navigate("earnings") }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = MenuIcon.Invite,
                        title = "邀请好友",
                        subtitle = userInfo?.inviteCode?.let { "邀请码: $it" },
                        onClick = { viewModel.loadInviteInfo() }
                    )
                }
            }

            item {
                MenuGroupCard {
                    MenuItemRow(
                        icon = MenuIcon.RealAuth,
                        title = "实名认证",
                        subtitle = getAuthStatusText(realAuthStatus),
                        onClick = { navController.navigate(TaskRoutes.REAL_AUTH) }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = MenuIcon.Wallet,
                        title = "钱包绑定",
                        onClick = { navController.navigate(TaskRoutes.WALLET_BINDING) }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = MenuIcon.AutoMode,
                        title = "自动模式",
                        subtitle = "手动",
                        onClick = { /* TODO */ }
                    )
                }
            }

            item {
                MenuGroupCard {
                    MenuItemRow(
                        icon = MenuIcon.Edit,
                        title = "编辑资料",
                        onClick = { navController.navigate(TaskRoutes.EDIT_PROFILE) }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = MenuIcon.Notification,
                        title = "消息通知",
                        onClick = { /* TODO */ }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = MenuIcon.About,
                        title = "关于我们",
                        onClick = { /* TODO */ }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = MenuIcon.Settings,
                        title = "设置",
                        onClick = { navController.navigate(TaskRoutes.SETTINGS) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.logout() }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ExitToApp,
                                contentDescription = "退出登录",
                                tint = LogoutRed,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "退出登录",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = LogoutRed
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showInviteDialog) {
        InviteDialog(
            inviteCode = inviteCode,
            inviteUrl = inviteUrl,
            onDismiss = { viewModel.dismissInviteDialog() }
        )
    }
}

// ==================== 邀请好友弹窗 ====================

@Composable
private fun InviteDialog(
    inviteCode: String?,
    inviteUrl: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("邀请好友", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text("邀请码", fontSize = 13.sp, color = Gray500)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = inviteCode ?: "加载中...",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = HallOrange
                )
                if (inviteUrl != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("邀请链接", fontSize = 13.sp, color = Gray500)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = inviteUrl,
                        fontSize = 13.sp,
                        color = Gray700,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("邀请码", inviteCode ?: ""))
                    android.widget.Toast.makeText(context, "邀请码已复制", android.widget.Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = HallOrange)
            ) {
                Text("复制邀请码", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ==================== 认证状态标签 ====================

@Composable
private fun AuthStatusBadge(status: Int) {
    val (text, bgColor, textColor) = when (status) {
        0 -> Triple("未认证", AuthUnverifiedYellow.copy(alpha = 0.2f), AuthUnverifiedYellow)
        1 -> Triple("审核中", AuthPendingBlue.copy(alpha = 0.2f), AuthPendingBlue)
        2 -> Triple("已认证", AuthVerifiedGreen.copy(alpha = 0.2f), AuthVerifiedGreen)
        3 -> Triple("认证失败", AuthFailedRed.copy(alpha = 0.2f), AuthFailedRed)
        else -> Triple("未认证", AuthUnverifiedYellow.copy(alpha = 0.2f), AuthUnverifiedYellow)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}

// ==================== 菜单分组卡片 ====================

@Composable
private fun MenuGroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

// ==================== 菜单项 ====================

private enum class MenuIcon(val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Task(Icons.Default.Assignment),
    Earnings(Icons.Default.AccountBalanceWallet),
    Invite(Icons.Default.People),
    RealAuth(Icons.Default.VerifiedUser),
    Wallet(Icons.Default.AccountBalance),
    AutoMode(Icons.Default.Autorenew),
    Notification(Icons.Default.Notifications),
    About(Icons.Default.Info),
    Settings(Icons.Default.Settings),
    Edit(Icons.Default.Edit)
}

@Composable
private fun MenuItemRow(
    icon: MenuIcon,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HallOrangeBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon.icon,
                contentDescription = title,
                tint = HallOrange,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Gray900
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Gray500
                )
            }
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Gray300,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ==================== 菜单分隔线 ====================

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        color = Gray100,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 70.dp)
    )
}

// ==================== 工具方法 ====================

private fun maskPhone(phone: String): String {
    if (phone.length < 7) return phone
    return phone.substring(0, 3) + "****" + phone.substring(phone.length - 4)
}

private fun getAuthStatusText(status: Int): String {
    return when (status) {
        0 -> "未认证"
        1 -> "审核中"
        2 -> "已认证"
        3 -> "认证失败"
        else -> "未认证"
    }
}

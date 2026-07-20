package com.task.platform.ui.profile

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.task.platform.ui.components.LoadingIndicator
import com.task.platform.ui.components.AppTopBar
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.task.platform.viewmodel.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── 配色 ───
private val Green = Color(0xFF07C160)
private val Blue = Color(0xFF1677FF)
private val GrayBg = Color(0xFFF8F9FA)
private val GrayBorder = Color(0xFFE8EAED)

data class WalletItem(
    val type: Int,      // 1=微信, 2=支付宝
    val name: String,
    val qrcodeUrl: String,
    val account: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletBindingScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val userInfo by viewModel.userInfo.collectAsState()

    // 进入页面即刷新资料：从 DataStore 缓存或后端 API 恢复 userInfo，
    // 解决重新进入页面时 hiltViewModel 新建实例导致列表为空的问题。
    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingWallet by remember { mutableStateOf<WalletItem?>(null) }

    // 删除确认弹窗状态
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WalletItem?>(null) }

    val wallets = userInfo?.let { info ->
        listOfNotNull(
            info.wechatQrcode?.let { WalletItem(1, "微信收款码", it, info.wechatAccount) },
            info.alipayQrcode?.let { WalletItem(2, "支付宝收款码", it, info.alipayAccount) }
        )
    } ?: emptyList()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "收款账户",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = GrayBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 说明卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ℹ", fontSize = 16.sp, color = Blue)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "请上传收款码并填写对应账号，奖励将发放至绑定账户",
                        fontSize = 13.sp,
                        color = Color(0xFF5F6368)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 已绑定列表
            if (wallets.isNotEmpty()) {
                Text(
                    "已绑定账户",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF202124),
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                wallets.forEach { wallet ->
                    WalletCard(
                        wallet = wallet,
                        onEdit = {
                            editingWallet = wallet
                            showAddEditDialog = true
                        },
                        onDelete = {
                            deleteTarget = wallet
                            showDeleteConfirm = true
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            if (wallets.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFFBDBDBD)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("暂未绑定收款账户", fontSize = 15.sp, color = Color(0xFF9E9E9E))
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // 添加按钮
            Button(
                onClick = {
                    editingWallet = null
                    showAddEditDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("添加收款账户", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showDeleteConfirm && deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("确认删除", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text("确定要删除「${target.name}」吗？\n删除后可重新添加。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            viewModel.unbindWallet(target.type) { success, msg ->
                                if (!success) {
                                    Toast.makeText(context, msg.ifBlank { "删除失败，请重试" }, Toast.LENGTH_SHORT).show()
                                }
                                // 成功后 unbindWallet 内部已刷新 profile，无需再 loadProfile
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = Color(0xFF5F6368))
                }
            }
        )
    }

    if (showAddEditDialog) {
        AddEditDialog(
            existing = editingWallet,
            onDismiss = { showAddEditDialog = false },
            onConfirm = { type, account, uri ->
                // 先关闭弹窗
                showAddEditDialog = false
                // 再异步调用 API：有图则上传绑定，无图则保留原图
                scope.launch {
                    // 失败重开弹窗：保持 editingWallet 状态以便回填（编辑场景保留原图）
                    val reopen = {
                        editingWallet = editingWallet?.copy(
                            type = type,
                            name = if (type == 1) "微信收款码" else "支付宝收款码"
                        ) ?: com.task.platform.ui.profile.WalletItem(type, "", "")
                        showAddEditDialog = true
                    }
                    if (uri != null) {
                        viewModel.bindWalletWithUri(
                            type = type,
                            account = account,
                            uri = uri
                        ) { success, _ ->
                            if (success) viewModel.loadProfile() else reopen()
                        }
                    } else {
                        // 编辑时保留原图；新增无图则传空串（后端按字段更新，不会清空已有图）
                        val keepQrcode = editingWallet?.qrcodeUrl ?: ""
                        viewModel.bindWallet(
                            type = type,
                            account = account,
                            qrcodeUrl = keepQrcode
                        ) { success, _ ->
                            if (success) viewModel.loadProfile() else reopen()
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun WalletCard(
    wallet: WalletItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (wallet.type == 1)
                                listOf(Green, Color(0xFF06B650))
                            else
                                listOf(Blue, Color(0xFF0D6EEA))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (wallet.type == 1) "微信" else "支付宝",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(wallet.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text("已绑定收款码", fontSize = 12.sp, color = Color(0xFF5F6368))
            }

            // 操作按钮
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "编辑", tint = GrayBorder, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDialog(
    existing: WalletItem?,
    onDismiss: () -> Unit,
    onConfirm: (type: Int, account: String, uri: Uri?) -> Unit
) {
    var selectedType by remember { mutableStateOf(existing?.type ?: 1) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var accountInput by remember { mutableStateOf(existing?.account ?: "") }
    var isLoading by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) selectedUri = uri
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                if (existing == null) "添加收款账户" else "编辑收款账户",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 类型选择（Material3 ExposedDropdownMenuBox，兼容 1.2.1 写法）
                Text("账户类型", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color(0xFF202124))
                Spacer(modifier = Modifier.height(8.dp))
                val typeOptions = listOf(1 to "微信收款码", 2 to "支付宝收款码")
                var typeExpanded by remember { mutableStateOf(false) }
                val typeSelectedLabel =
                    typeOptions.firstOrNull { it.first == selectedType }?.second ?: typeOptions[0].second
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = typeSelectedLabel,
                        onValueChange = { },
                        label = { Text("账户类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Blue,
                            unfocusedBorderColor = GrayBorder
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        typeOptions.forEach { (type, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 图片选择
                Text("收款码图片", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color(0xFF202124))
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, GrayBorder, RoundedCornerShape(12.dp))
                        .background(Color(0xFFF5F5F5))
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedUri != null) {
                        ThumbnailImage(
                            uri = selectedUri!!,
                            modifier = Modifier.fillMaxSize()
                        )
                        // 重新选择蒙层
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("点击重新选择", color = Color.White, fontSize = 12.sp)
                        }
                    } else if (existing != null) {
                        AsyncImage(
                            model = existing.qrcodeUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        // 编辑模式：提示可点击更换收款码
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("点击更换收款码", color = Color.White, fontSize = 12.sp)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AddAPhoto,
                                contentDescription = null,
                                tint = Color(0xFF9E9E9E),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("点击上传收款码", color = Color(0xFF9E9E9E), fontSize = 14.sp)
                            Text("支持 jpg、png 格式", color = Color(0xFFBDBDBD), fontSize = 11.sp)
                        }
                    }
                }

                // 账号输入（选填，新增/编辑均展示，支持"只填账号"绑定）
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = accountInput,
                    onValueChange = { accountInput = it },
                    label = { Text(if (selectedType == 1) "微信账号（选填）" else "支付宝账号（选填）") },
                    placeholder = { Text("请输入收款账号，方便核对") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Blue,
                        unfocusedBorderColor = GrayBorder
                    )
                )
            }
        },
        confirmButton = {
            val isAddMode = existing == null
            val canConfirm = if (isAddMode) {
                (selectedUri != null || accountInput.isNotBlank()) && !isLoading
            } else {
                !isLoading
            }
            Button(
                onClick = {
                    if (!isLoading) {
                        isLoading = true
                        onConfirm(selectedType, accountInput, selectedUri)
                    }
                },
                enabled = canConfirm,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedType == 1) Green else Blue,
                    disabledContainerColor = GrayBorder
                )
            ) {
                if (isLoading) {
                    LoadingIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (isLoading) "提交中..." else "确认绑定",
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("取消", color = Color(0xFF5F6368))
            }
        }
    )
}

/** 从 ScreenshotUploadScreen.kt 复制 */
@Composable
private fun ThumbnailImage(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    var errored by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        try {
            val result = withContext(Dispatchers.IO) {
                var bmp: android.graphics.Bitmap? = null
                try {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        bmp = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
                        pfd.close()
                    }
                } catch (_: Exception) { }
                if (bmp == null) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            bmp = BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 2 })
                        }
                    } catch (_: Exception) { }
                }
                bmp?.asImageBitmap()
            }
            bitmap = result
            errored = (result == null)
        } catch (e: Exception) {
            errored = true
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bmp = bitmap
        when {
            bmp != null -> Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            errored -> Text("加载失败", fontSize = 10.sp, color = Color.Gray)
            else -> LoadingIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

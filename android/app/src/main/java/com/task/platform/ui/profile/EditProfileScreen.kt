package com.task.platform.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.task.platform.ui.components.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.task.platform.R
import com.task.platform.viewmodel.ProfileViewModel
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 编辑资料屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val userInfo by viewModel.userInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var nickname by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var avatarLocalUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showImagePicker by remember { mutableStateOf(false) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var uploadingAvatar by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 初始化：从 userInfo 恢复
    LaunchedEffect(userInfo) {
        if (nickname.isBlank() && userInfo != null) {
            nickname = userInfo?.nickname ?: ""
            avatarUrl = userInfo?.avatarUrl ?: ""
        }
    }

    // 图片选择器 — 选图后立刻显示本地预览，同时后台上传
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                android.util.Log.d("Profile", "EditProfile: picked image uri=$it")
                avatarLocalUri = it       // 立即显示本地图片
                uploadingAvatar = true
                scope.launch {
                    viewModel.uploadAvatar(it) { success, url ->
                        uploadingAvatar = false
                        android.util.Log.d("Profile", "EditProfile: upload result success=$success, url=$url")
                        if (success) {
                            avatarUrl = url
                        } else {
                            android.widget.Toast.makeText(context, "头像上传失败", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑资料") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showSaveConfirm = true },
                        enabled = !isLoading
                    ) {
                        Text("保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // 头像
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .clickable { showImagePicker = true },
                    tonalElevation = 2.dp,
                ) {
                    if (avatarLocalUri != null) {
                        ThumbnailImage(
                            uri = avatarLocalUri!!,
                            modifier = Modifier.fillMaxSize()
                        )
                        // 上传中蒙层
                        if (uploadingAvatar) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    } else if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val initial = if (nickname.isNotBlank()) nickname.first().toString() else "用"
                        Box(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 点击更换头像提示
            TextButton(
                onClick = { showImagePicker = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("更换头像")
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // 昵称输入
            Text(text = "昵称", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                placeholder = { Text("请输入昵称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 图片选择对话框
    if (showImagePicker) {
        AlertDialog(
            onDismissRequest = { showImagePicker = false },
            title = { Text("选择头像来源") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            showImagePicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("从相册选择")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImagePicker = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 保存确认对话框
    if (showSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text("保存资料") },
            text = { Text("确定要保存修改吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveConfirm = false
                        viewModel.updateProfile(nickname, avatarUrl) { success, msg ->
                            if (success) {
                                android.widget.Toast.makeText(context, "保存成功", android.widget.Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            } else {
                                android.widget.Toast.makeText(context, msg.ifBlank { "保存失败" }, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        LoadingIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("确定")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/** 从本地 URI 加载并显示图片（与 WalletBindingScreen 相同实现） */
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
        } catch (_: Exception) {
            errored = true
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bmp = bitmap
        when {
            bmp != null -> Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            errored -> Text("加载失败", fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.Gray)
            else -> LoadingIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

package com.task.platform.ui.task

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import com.task.platform.ui.components.LoadingIndicator
import com.task.platform.ui.components.AppTopBar
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.task.platform.viewmodel.TaskViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotUploadScreen(
    navController: NavController,
    taskId: Long,
) {
    val viewModel: TaskViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) selectedUris = selectedUris + uris
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is TaskViewModel.UiState.SubmitSuccess -> {
                viewModel.loadTaskRecord(taskId)
                snackbarHostState.showSnackbar("截图已提交，请等待审核")
                navController.popBackStack()
            }
            is TaskViewModel.UiState.Error -> {
                snackbarHostState.showSnackbar((uiState as TaskViewModel.UiState.Error).message)
                viewModel.resetError()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "上传截图",
                onBackClick = { navController.popBackStack() }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("请上传任务完成截图（${selectedUris.size}张）", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // ===== 3列网格 =====
            if (selectedUris.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedUris.chunked(3).forEach { rowUris ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowUris.forEach { uri ->
                                // 每个格子
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE0E0E0))
                                ) {
                                    ThumbnailImage(uri)
                                    // 删除按钮
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .clickable { selectedUris = selectedUris.filter { it != uri } },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Close, "删除",
                                            tint = Color.White, modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            repeat(3 - rowUris.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AddAPhoto, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedUris.isEmpty()) "选择截图" else "继续添加")
            }

            Spacer(modifier = Modifier.weight(1f))

            // ===== 进度显示 =====
            val isSubmitting = uiState is TaskViewModel.UiState.Submitting
            if (isSubmitting) {
                val progress = (uiState as TaskViewModel.UiState.Submitting).progress
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(progress, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // ===== 提交按钮 =====
            Button(
                onClick = { scope.launch { viewModel.submitTaskWithBatchUpload(taskId, selectedUris) } },
                enabled = selectedUris.isNotEmpty() && !isSubmitting,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text("提交审核（${selectedUris.size}张）", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ==================== 缩略图 ====================

@Composable
private fun ThumbnailImage(uri: Uri) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    var errored by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        try {
            val result = withContext(Dispatchers.IO) {
                android.util.Log.d("Thumbnail", "=== 开始加载图片 ===")
                android.util.Log.d("Thumbnail", "URI: $uri")
                android.util.Log.d("Thumbnail", "URI scheme: ${uri.scheme}, authority: ${uri.authority}, path: ${uri.path}")

                // 尝试1: checkContentResolution 检查 MIME type
                val mimeType = context.contentResolver.getType(uri)
                android.util.Log.d("Thumbnail", "MIME type: $mimeType")

                // 尝试2: openFileDescriptor
                var bmp: android.graphics.Bitmap? = null
                try {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        android.util.Log.d("Thumbnail", "openFileDescriptor 成功")
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        bmp = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
                        pfd.close()
                        if (bmp != null) {
                            val w = bmp!!.width; val h = bmp!!.height
                            android.util.Log.d("Thumbnail", "decodeFileDescriptor 成功: ${w}x${h}")
                        } else {
                            android.util.Log.e("Thumbnail", "decodeFileDescriptor 返回 null")
                        }
                    } else {
                        android.util.Log.e("Thumbnail", "openFileDescriptor 返回 null")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Thumbnail", "openFileDescriptor 异常: ${e.message}", e)
                }

                // 尝试3: 如果 FileDescriptor 失败，试 InputStream + decodeStream
                if (bmp == null) {
                    android.util.Log.d("Thumbnail", "尝试 InputStream 方式...")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                            bmp = BitmapFactory.decodeStream(stream, null, opts)
                            if (bmp != null) {
                                val w = bmp!!.width; val h = bmp!!.height
                                android.util.Log.d("Thumbnail", "decodeStream 成功: ${w}x${h}")
                            } else {
                                android.util.Log.e("Thumbnail", "decodeStream 返回 null")
                            }
                        } ?: android.util.Log.e("Thumbnail", "openInputStream 返回 null")
                    } catch (e: Exception) {
                        android.util.Log.e("Thumbnail", "InputStream 异常: ${e.message}", e)
                    }
                }

                // 尝试4: 最后的备选 — 不降采样直接解码
                if (bmp == null) {
                    android.util.Log.d("Thumbnail", "尝试无降采样 InputStream...")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val opts = BitmapFactory.Options()
                            bmp = BitmapFactory.decodeStream(stream, null, opts)
                            if (bmp != null) {
                                val w = bmp!!.width; val h = bmp!!.height
                                android.util.Log.d("Thumbnail", "无降采样 成功: ${w}x${h}")
                            } else {
                                android.util.Log.e("Thumbnail", "无降采样 decodeStream 也返回 null！")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Thumbnail", "无降采样 异常: ${e.message}", e)
                    }
                }

                bmp?.asImageBitmap()
            }
            bitmap = result
            if (result == null) {
                android.util.Log.e("Thumbnail", "=== 最终结果：解码失败 ===")
                errored = true
            } else {
                android.util.Log.d("Thumbnail", "=== 最终结果：解码成功 ===")
                errored = false
            }
        } catch (e: Exception) {
            android.util.Log.e("Thumbnail", "外层异常: ${e.message}", e)
            e.printStackTrace()
            errored = true
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        when {
            bmp != null -> Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            errored -> Text("加载失败", fontSize = 10.sp, color = Color.Gray)
            else -> LoadingIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

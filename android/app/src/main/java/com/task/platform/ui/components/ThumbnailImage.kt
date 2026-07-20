package com.task.platform.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通用缩略图组件：手动解码 Uri（支持 content://）为 ImageBitmap 显示。
 * 不用 `by` 委托，改用 = + .value，避免跨文件委托解析问题。
 *
 * 接入 Design System：加载态统一使用 [LoadingIndicator]，文案字号使用语义层级。
 */
@Composable
fun ThumbnailImage(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val bitmap = remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    val errored = remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        try {
            val result = withContext(Dispatchers.IO) {
                android.util.Log.d("ThumbnailImage", "URI: $uri, scheme: ${uri.scheme}")

                var bmp: android.graphics.Bitmap? = null

                // 尝试1: openFileDescriptor
                try {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        bmp = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
                        pfd.close()
                        if (bmp != null) {
                            android.util.Log.d("ThumbnailImage", "decodeFileDescriptor 成功: ${bmp.width}x${bmp.height}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ThumbnailImage", "decodeFileDescriptor 失败: ${e.message}")
                }

                // 尝试2: openInputStream fallback
                if (bmp == null) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                            bmp = BitmapFactory.decodeStream(stream, null, opts)
                            if (bmp != null) {
                                android.util.Log.d("ThumbnailImage", "decodeStream 成功: ${bmp.width}x${bmp.height}")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ThumbnailImage", "decodeStream 失败: ${e.message}")
                    }
                }

                bmp?.asImageBitmap()
            }
            bitmap.value = result
            errored.value = (result == null)
        } catch (e: Exception) {
            android.util.Log.e("ThumbnailImage", "异常: ${e.message}", e)
            errored.value = true
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bmp = bitmap.value
        when {
            bmp != null -> Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
            errored.value -> Text(
                "加载失败",
                fontSize = 10.sp, // TODO 功能尺寸：缩略图占位极小注记，非页面语义字号
                color = Color.Gray
            )
            else -> LoadingIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

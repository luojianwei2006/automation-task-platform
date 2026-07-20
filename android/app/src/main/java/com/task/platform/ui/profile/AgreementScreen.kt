package com.task.platform.ui.profile

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.task.platform.ui.components.LoadingIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.task.platform.BuildConfig
import com.task.platform.model.AgreementVO
import com.task.platform.network.ApiClient

/**
 * 协议展示页
 *
 * 通过匿名接口 [com.task.platform.network.ApiClient.getAgreement] 拉取指定类型的协议 HTML，
 * 用 [WebView] 渲染（baseUrl = [BuildConfig.BASE_URL]，使 HTML 中的相对图片 URL 正确解析）。
 *
 * 路由：agreement/{type}，type ∈ {about, privacy, register}
 *
 * @param navController 导航控制器
 * @param type          协议类型
 */
@Composable
fun AgreementScreen(navController: NavController, type: String) {
    var agreement by remember { mutableStateOf<AgreementVO?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 每次进入按 type 拉取最新（不缓存）
    LaunchedEffect(type) {
        loading = true
        errorMsg = null
        agreement = null
        try {
            val resp = ApiClient.apiService.getAgreement(type)
            if (resp.code == 200 && resp.data != null) {
                agreement = resp.data
            } else {
                errorMsg = resp.msg ?: "加载失败"
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "网络错误，请稍后重试"
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = agreement?.title ?: "协议",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }

            errorMsg != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error)
                }
            }

            (agreement?.contentHtml ?: "").isBlank() -> {
                // 空态提示
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "暂无协议内容",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // WebView 渲染协议 HTML
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = false
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                webViewClient = WebViewClient()
                            }
                        },
                        update = { webView ->
                            val html = buildAgreementHtml(agreement?.contentHtml ?: "")
                            webView.loadDataWithBaseURL(
                                BuildConfig.BASE_URL,
                                html,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    )

                    // 底部版本信息
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "更新于 ${agreement?.updatedAt ?: "-"} · v${agreement?.version ?: 0}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * 拼装带移动端适配样式的完整 HTML（与后端/前端 CSS 模板一致，
 * 图片相对 URL 由 baseUrl = BuildConfig.BASE_URL 解析）。
 */
private fun buildAgreementHtml(content: String): String {
    val css = """
        body{margin:0;padding:16px;font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;
             font-size:16px;line-height:1.7;color:#222;word-wrap:break-word;word-break:break-word;}
        img{max-width:100%!important;height:auto!important;border-radius:6px;margin:8px 0;}
        a{color:#ff8c00;word-break:break-all;}
        h1,h2,h3{line-height:1.4;}
        pre{white-space:pre-wrap;background:#f6f6f6;padding:10px;border-radius:6px;overflow-x:auto;}
        table{border-collapse:collapse;width:100%;}
        td,th{border:1px solid #ddd;padding:6px;}
    """.trimIndent()
    return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">" +
            "<style>$css</style></head><body>$content</body></html>"
}

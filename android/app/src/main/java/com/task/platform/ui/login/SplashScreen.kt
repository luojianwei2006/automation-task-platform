package com.task.platform.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.task.platform.R
import com.task.platform.network.ApiClient
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

// 配色：融合三平台元素（抖音青 / 快手橙 / 金币黄 / 桃粉）
private val SplashDouyin = Color(0xFF00E5FF)      // 抖音青
private val SplashKuaishou = Color(0xFFFF6B00)    // 快手橙
private val SplashGold = Color(0xFFFFC107)        // 金币黄
private val SplashPeach = Color(0xFFFFCCBC)       // 桃粉（保留尾部柔和过渡）

/**
 * 启动页
 * 橙色主题渐变背景 + Logo缩放动画 + 装饰圆
 */
@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToMain: () -> Unit = onNavigateToLogin
) {
    val context = LocalContext.current
    var showContent by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }
    var showVersion by remember { mutableStateOf(false) }

    // Logo 缩放动画
    var logoScale by remember { mutableStateOf(0.6f) }
    val animatedScale by animateFloatAsState(
        targetValue = logoScale,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "logoScale"
    )

    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "v${packageInfo.versionName}"
        } catch (e: Exception) {
            "v1.0.0"
        }
    }

    LaunchedEffect(Unit) {
        // 启动时从 DataStore 恢复 token 到 ApiClient
        val provider = EntryPointAccessors.fromApplication(
            context,
            SplashDataStoreProvider::class.java
        )
        val dataStoreManager = provider.dataStoreManager()
        val token = dataStoreManager.getTokenSync()
        val hasToken = !token.isNullOrBlank()
        if (hasToken) {
            ApiClient.setToken(token!!)
        }

        delay(200) // 稍微延迟启动动画
        showContent = true
        logoScale = 1f
        delay(400)
        showSubtitle = true
        delay(300)
        showVersion = true
        delay(1100) // 剩余停留时间

        // 已登录 → 直接进主页，未登录 → 去登录页
        if (hasToken) {
            onNavigateToMain()
        } else {
            onNavigateToLogin()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SplashDouyin, SplashKuaishou, SplashGold, SplashPeach)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // 装饰圆（背景点缀）
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(y = (-200).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = 120.dp, y = 250.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = (-140).dp, y = 180.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo 区域
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(600))
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(animatedScale)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = "Logo",
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 主标题
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(600))
            ) {
                Text(
                    text = "任务平台",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 副标题
            AnimatedVisibility(
                visible = showSubtitle,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(500)
                ) + fadeIn(tween(500))
            ) {
                Text(
                    text = "抖音 · 快手任务 · 赚取奖励",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            // 版本号
            AnimatedVisibility(
                visible = showVersion,
                enter = fadeIn(tween(400))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                    Text(
                        text = versionName,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

/**
 * Hilt EntryPoint，用于在 @Composable 中获取 DataStoreManager
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SplashDataStoreProvider {
    fun dataStoreManager(): com.task.platform.storage.DataStoreManager
}

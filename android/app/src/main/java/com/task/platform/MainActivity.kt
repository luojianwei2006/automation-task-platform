package com.task.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.task.platform.navigation.TaskNavGraph
import kotlinx.coroutines.delay
import com.task.platform.update.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * 安卓端主Activity - Jetpack Compose入口
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 存储权限请求启动器（截图需要读取 /sdcard/ 文件） */
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "需要存储权限才能截图，请在设置中手动授权", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestStoragePermissionIfNeeded()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val updateViewModel: UpdateViewModel = hiltViewModel()
                    // App 启动后检查一次版本更新（可选更新，失败不影响正常使用）。
                    // 延迟至 App 前台稳定、Splash（约 2 秒）尾声再查，避免冷启动系统网络
                    // 未就绪导致首个请求偶发失败；delay 在 LaunchedEffect 默认 Main 协程中
                    // 非阻塞，安全。
                    LaunchedEffect(Unit) {
                        delay(1200)
                        updateViewModel.checkUpdate()
                    }
                    TaskNavGraph(
                        navController = navController,
                        updateViewModel = updateViewModel
                    )
                }
            }
        }
    }

    /** 请求必要的运行权限（存储 + 通知） */
    private fun requestStoragePermissionIfNeeded() {
        val permissions = mutableListOf<String>()

        // 存储权限
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // 通知权限（Android 13+，targetSdk >= 33 时必须，否则 startForeground 崩溃）
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            android.util.Log.d("MainActivity", "请求权限: $permissions")
            storagePermissionLauncher.launch(permissions.toTypedArray())
        } else {
            android.util.Log.d("MainActivity", "所有权限已授权")
        }
    }
}

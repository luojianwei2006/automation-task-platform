package com.task.platform.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.task.platform.BuildConfig
import com.task.platform.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App 更新管理器（Hilt 单例）
 *
 * 职责：
 *   1. 调后端 GET /api/user/config 拉取最新版本配置；
 *   2. 与本地 [BuildConfig.VERSION_NAME] 比对，判断是否有新版本；
 *   3. 通过 [OkHttpClient] 流式下载 APK，并在下载过程中实时回调进度（总大小 / 已下载 / 百分比）；
 *   4. 下载完成后用 FileProvider + ACTION_VIEW 调起系统安装器。
 *
 * 状态以 [StateFlow] 暴露给 UI（[UpdateViewModel] / Compose 弹窗）。
 *
 * 兼容性说明：
 *   - 下载落盘用 getExternalFilesDir()/apk/，对应 file_paths.xml 的 external-files-path，
 *     无需存储权限，App 外部私有目录可被 FileProvider 暴露给系统安装器。
 *   - Android 8+ 未知来源安装：未授权时引导用户去设置开启（REQUEST_INSTALL_PACKAGES 已声明）。
 *
 * 进度说明：
 *   - 服务端若返回 Content-Length，则 totalBytes > 0，可计算百分比；
 *   - 服务端未返回 Content-Length 时 totalBytes = -1，此时进度条使用不确定模式。
 */
@Singleton
class AppUpdateManager @Inject constructor(
    private val apiClient: ApiClient
) {
    /** HTTP 客户端（流式下载 APK） */
    private val httpClient = OkHttpClient()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** 缓存最新版本配置，供「立即更新」时取下载地址 */
    private var latestConfig: AppConfig? = null

    /**
     * 检查更新（挂起函数，应在 IO 协程中调用）。
     */
    suspend fun checkUpdate() {
        _updateState.value = UpdateState.Checking
        try {
            val response = apiClient.apiService.getAppConfig()
            if (response.code == 200 && response.data != null) {
                val data = response.data
                val remoteVersion = (data["app_version"] ?: "").trim()
                val downloadUrl = (data["app_download_url"] ?: "").trim()
                val appName = (data["app_name"] ?: "").trim()

                if (remoteVersion.isNotEmpty() && downloadUrl.isNotEmpty()
                    && UpdateChecker.isNewVersion(remoteVersion, BuildConfig.VERSION_NAME)
                ) {
                    latestConfig = AppConfig(remoteVersion, downloadUrl, appName)
                    _updateState.value = UpdateState.Available(remoteVersion, downloadUrl, appName)
                } else {
                    _updateState.value = UpdateState.Idle
                }
            } else {
                _updateState.value = UpdateState.Idle
            }
        } catch (e: Exception) {
            Log.w("AppUpdateManager", "检查版本更新失败: ${e.message}", e)
            _updateState.value = UpdateState.Idle
        }
    }

    /**
     * 开始下载并安装（由 UI「立即更新」按钮触发）。
     *
     * 该方法为挂起函数，内部通过 [OkHttpClient] 流式下载 APK，
     * 并持续通过 [UpdateState.Downloading] 上报进度（总大小 / 已下载 / 百分比）。
     * 下载完成后直接调用 [installApk] 调起系统安装器。
     *
     * 注意：下载是阻塞 IO，调用方应在 IO 调度器（或 viewModelScope.launch(Dispatchers.IO)）中调用。
     */
    suspend fun startDownload(context: Context) {
        val config = latestConfig
        if (config == null || config.url.isEmpty()) {
            _updateState.value = UpdateState.Error("未获取到下载地址，请稍后重试")
            return
        }
        _updateState.value = UpdateState.Downloading(0, -1, 0)
        try {
            val file = apkFile(context)
            file.parentFile?.mkdirs()
            downloadApk(context, config.url) { downloaded, total ->
                val percent = if (total > 0) {
                    ((downloaded * 100 / total).toInt()).coerceIn(0, 100)
                } else {
                    0
                }
                _updateState.value = UpdateState.Downloading(downloaded, total, percent)
            }
            installApk(context, file) // 保持不变
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "下载失败: ${e.message}", e)
            _updateState.value = UpdateState.Error("下载失败：${e.message}")
        }
    }

    /**
     * 通过 [OkHttpClient] 流式下载 APK 到 [apkFile]，并在下载过程中持续回调进度。
     *
     * @param url        下载地址
     * @param onProgress 进度回调，参数为 (已下载字节数, 总字节数)；
     *                   总字节数可能为 -1（服务端未返回 Content-Length）。
     */
    private suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val file = apkFile(context)
            file.parentFile?.mkdirs()

            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("下载失败：HTTP ${response.code} ${response.message}")
                }
                val body = response.body ?: throw IOException("下载失败：响应体为空")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var downloaded = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        output.flush()
                    }
                }
            }
        }
    }

    /**
     * 调起系统安装器安装 APK。
     */
    fun installApk(context: Context, file: File) {
        if (!file.exists()) {
            Log.e("AppUpdateManager", "APK 文件不存在: ${file.absolutePath}")
            Toast.makeText(context, "安装包不存在", Toast.LENGTH_SHORT).show()
            return
        }

        // Android 8+ 未知来源安装限制：未授权则引导去设置开启
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && !context.packageManager.canRequestPackageInstalls()
        ) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "请先开启「允许安装未知应用」后再试", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "请到系统设置开启未知来源安装权限", Toast.LENGTH_LONG).show()
            }
            return
        }

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "启动安装器失败: ${e.message}", e)
            Toast.makeText(context, "无法启动安装器：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 用户点击「稍后再说」/ 关闭弹窗 → 回到空闲态（可选更新，不影响使用）。
     */
    fun dismiss() {
        latestConfig = null
        _updateState.value = UpdateState.Idle
    }
}

/**
 * APK 下载落盘文件。
 * 使用 App 外部私有目录 getExternalFilesDir()/apk/，
 * 与 res/xml/file_paths.xml 的 external-files-path（name=apk, path=apk/）对应。
 */
fun apkFile(context: Context): File {
    val root = context.getExternalFilesDir(null) ?: context.filesDir
    return File(root, "apk/app-release.apk")
}

/**
 * 最新版本配置（缓存用）。
 */
data class AppConfig(
    val version: String,
    val url: String,
    val appName: String
)

package com.task.platform.update

import android.app.DownloadManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App 更新管理器（Hilt 单例）
 *
 * 职责：
 *   1. 调后端 GET /api/user/config 拉取最新版本配置；
 *   2. 与本地 [BuildConfig.VERSION_NAME] 比对，判断是否有新版本；
 *   3. 通过系统 [DownloadManager] 把 APK 下载到 App 外部私有目录；
 *   4. 下载完成后由 [DownloadCompleteReceiver] 回调 [onDownloadComplete]，
 *      再用 FileProvider + ACTION_VIEW 调起系统安装器。
 *
 * 状态以 [StateFlow] 暴露给 UI（[UpdateViewModel] / Compose 弹窗）。
 *
 * 兼容性说明：
 *   - 下载落盘用 getExternalFilesDir()/apk/，原因：DownloadManager 运行在独立系统进程，
 *     无法写入 App 内部私有目录（/data/data/.../files/），但可写入 App 外部私有目录。
 *   - file_paths.xml 的 external-files-path 与下载目录、FileProvider authority 三者对应。
 *   - Android 8+ 未知来源安装：未授权时引导用户去设置开启（REQUEST_INSTALL_PACKAGES 已声明）。
 */
@Singleton
class AppUpdateManager @Inject constructor(
    private val apiClient: ApiClient
) {
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
     */
    fun startDownload(context: Context) {
        val config = latestConfig
        if (config == null || config.url.isEmpty()) {
            _updateState.value = UpdateState.Error("未获取到下载地址，请稍后重试")
            return
        }
        _updateState.value = UpdateState.Downloading
        try {
            val file = apkFile(context)
            file.parentFile?.mkdirs()
            val request = DownloadManager.Request(Uri.parse(config.url)).apply {
                setTitle(config.appName.ifBlank { "任务平台" })
                setDescription("正在下载新版本 v${config.version}")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, null, "apk/app-release.apk")
                setMimeType("application/vnd.android.package-archive")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Log.d("AppUpdateManager", "已下发下载任务: ${config.url}")
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "下载失败: ${e.message}", e)
            _updateState.value = UpdateState.Error("下载失败：${e.message}")
        }
    }

    /**
     * 下载完成回调（由 [DownloadCompleteReceiver] 调用）。
     */
    fun onDownloadComplete(context: Context, downloadId: Long) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            dm.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        installApk(context, apkFile(context))
                        return
                    }
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    Log.e("AppUpdateManager", "下载未成功完成 status=$status reason=$reason")
                    Toast.makeText(context, "下载失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    _updateState.value = UpdateState.Error("下载失败，请稍后重试")
                } else {
                    _updateState.value = UpdateState.Error("下载失败，请稍后重试")
                }
            }
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "处理下载完成事件失败: ${e.message}", e)
            Toast.makeText(context, "安装失败：${e.message}", Toast.LENGTH_SHORT).show()
            _updateState.value = UpdateState.Error("安装失败：${e.message}")
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSIONS)
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

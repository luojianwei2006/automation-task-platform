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
import com.task.platform.storage.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
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
 *
 * 断点续传说明（核心能力）：
 *   - 下载先写入临时片段文件 `app_update_${remoteVersion}.apk.part`（文件名带版本，避免不同版本残片互相污染）；
 *   - 开始下载前若片段已存在且 size > 0，则以该 size 作为偏移量，请求头带 `Range: bytes=existing-`；
 *   - 服务端返回 **206 Partial Content** → 以**追加模式**写文件，totalBytes 取 Content-Range 解析出的完整大小；
 *   - 服务端返回 **200**（忽略 Range，不支持断点）→ **先删除已有片段，从头完整写入**（避免文件损坏）；
 *   - 下载成功后 rename `.part` → `.apk`，再走 [installApk]；
 *   - 用户主动取消（[cancelDownload]）仅置取消标记并中断网络，但**保留 .part**，使「再次点击下载」或
 *     「进程被杀重启后再次下载」都能从磁盘片段继续。
 */
@Singleton
class AppUpdateManager @Inject constructor(
    private val apiClient: ApiClient,
    private val dataStoreManager: DataStoreManager
) {
    /** HTTP 客户端（流式下载 APK） */
    private val httpClient = OkHttpClient()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** 缓存最新版本配置，供「立即更新」时取下载地址 */
    private var latestConfig: AppConfig? = null

    /** 进程级自动检查去重标记：单例存活期间，开机自动检查只发起一次 */
    private var autoChecked = false

    /** 当前下载是否已被用户取消（用于中断 in-flight 下载，不视为失败，保留 .part 以便续传） */
    private val cancelRequested = AtomicBoolean(false)

    /** 当前下载的 OkHttp Call，便于取消时立即中断底层网络读取 */
    @Volatile
    private var currentCall: Call? = null

    /**
     * 检查更新（挂起函数，应在 IO 协程中调用）。
     *
     * @param isAuto 是否由开机自动触发。为 true 时受进程级去重约束
     *              （[autoChecked] 为 true 则直接返回，不重复发起请求、不动 state）；
     *              手动检查应传 false，随时可查不受去重约束。
     *
     * 失败重试：当接口异常（code != 200 或 data 为空）或网络异常时，
     * 最多重试 2 次，间隔约 2 秒；"无新版本"分支（远端版本为空 / 下载地址为空 /
     * 远端版本不高于本地）属于正常结果，不重试。仅当所有重试均失败时返回 isError。
     */
    suspend fun checkUpdate(isAuto: Boolean = false): CheckResult {
        // 进程级去重：开机自动检查每进程只跑一次，避免冷启动期间重复请求
        if (isAuto && autoChecked) {
            Log.d("AppUpdateManager", "自动检查已完成（单例存活期间去重），跳过重复请求")
            return CheckResult(message = "已自动检查过")
        }

        _updateState.value = UpdateState.Checking
        Log.d("AppUpdateManager", "checkUpdate 开始，本地版本=${BuildConfig.VERSION_NAME}, isAuto=$isAuto")

        val maxRetries = 2
        var attempt = 0
        var lastError: String? = null

        while (attempt <= maxRetries) {
            if (attempt > 0) {
                Log.w("AppUpdateManager", "检查失败，第 $attempt 次重试...")
                delay(2000)
            }
            try {
                val response = apiClient.apiService.getAppConfig()
                Log.d("AppUpdateManager", "配置接口返回：code=${response.code}, dataNull=${response.data == null}, attempt=$attempt")
                if (response.code == 200 && response.data != null) {
                    val data = response.data
                    val remoteVersion = (data["app_version"] ?: "").trim()
                    val downloadUrl = (data["app_download_url"] ?: "").trim()
                    val appName = (data["app_name"] ?: "").trim()
                    Log.d("AppUpdateManager", "解析配置：app_version=$remoteVersion, app_download_url=$downloadUrl, app_name=$appName")

                    // 解析并持久化「注册时是否验证手机号」开关（来自 /api/user/config 的 require_phone_verify）
                    val requirePhoneVerifyRaw = (data["require_phone_verify"] ?: "true").trim()
                    val requirePhoneVerify = requirePhoneVerifyRaw != "false"
                    dataStoreManager.setRequirePhoneVerify(requirePhoneVerify)
                    Log.d("AppUpdateManager", "解析配置：require_phone_verify=$requirePhoneVerify")

                    if (remoteVersion.isNotEmpty() && downloadUrl.isNotEmpty()
                        && UpdateChecker.isNewVersion(remoteVersion, BuildConfig.VERSION_NAME)
                    ) {
                        latestConfig = AppConfig(remoteVersion, downloadUrl, appName)
                        Log.d("AppUpdateManager", "发现新版本：remote=$remoteVersion, url=$downloadUrl")
                        _updateState.value = UpdateState.Available(remoteVersion, downloadUrl, appName)
                        autoChecked = true
                        return CheckResult(hasUpdate = true, message = remoteVersion)
                    } else {
                        // 无新版本：非网络故障，不重试，直接返回最新版本结论
                        if (remoteVersion.isEmpty()) {
                            Log.d("AppUpdateManager", "app_version 为空，不弹窗")
                        } else if (downloadUrl.isEmpty()) {
                            Log.d("AppUpdateManager", "app_download_url 为空，不弹窗")
                        } else {
                            Log.d("AppUpdateManager", "远端版本=$remoteVersion 不高于本地=${BuildConfig.VERSION_NAME}，不弹窗")
                        }
                        _updateState.value = UpdateState.Idle
                        autoChecked = true
                        return CheckResult(message = "已是最新版本")
                    }
                } else {
                    // 接口异常分支：code != 200 或 data 为空，可重试
                    lastError = "接口返回异常 code=${response.code}"
                    Log.w("AppUpdateManager", "配置接口异常：code=${response.code}, dataNull=${response.data == null}，请确认 BASE_URL 与后端是否启动")
                    attempt++
                    continue
                }
            } catch (e: Exception) {
                lastError = e.message ?: "请求异常"
                Log.w("AppUpdateManager", "检查版本更新失败: ${e.message}", e)
                attempt++
                continue
            }
        }

        // 所有重试均失败：仅此时才以错误结束（非网络故障分支已在循环内提前返回）
        Log.e("AppUpdateManager", "检查更新失败，已重试 $maxRetries 次后放弃：$lastError")
        _updateState.value = UpdateState.Idle
        autoChecked = true
        return CheckResult(isError = true, message = lastError ?: "检查失败")
    }

    /**
     * 开始下载并安装（由 UI「立即更新」按钮触发）。
     *
     * 该方法为挂起函数，内部通过 [OkHttpClient] 流式下载 APK，
     * 并持续通过 [UpdateState.Downloading] 上报进度（总大小 / 已下载 / 百分比）。
     * 下载完成后直接调用 [installApk] 调起系统安装器。
     *
     * 断点续传：方法内部会自动判断磁盘上是否已有同版本的 `.part` 片段——
     * 若存在则从该偏移量继续（核心能力，无需调用方感知）。因此无论是
     * 「再次点击下载」还是「进程被杀重启后再次下载」都能续传。
     *
     * 注意：下载是阻塞 IO，调用方应在 IO 调度器（或 viewModelScope.launch(Dispatchers.IO)）中调用。
     */
    suspend fun startDownload(context: Context) {
        val config = latestConfig
        if (config == null || config.url.isEmpty()) {
            _updateState.value = UpdateState.Error("未获取到下载地址，请稍后重试")
            return
        }
        // 新的一轮下载：重置取消标记与上一轮的 Call 引用
        cancelRequested.set(false)
        currentCall = null

        // 开始新版本下载前，清理其它版本的 .part 残片（文件名前缀匹配）
        cleanupStalePartFiles(context, config.version)

        val partFile = updatePartFile(context, config.version)
        val finalFile = updateApkFile(context, config.version)

        // 续传起点：已存在的片段大小（用于初始进度展示）
        val existing = if (partFile.exists() && partFile.length() > 0) partFile.length() else 0L
        _updateState.value = UpdateState.Downloading(existing, -1, 0)

        try {
            downloadApk(config.url, partFile) { downloaded, total ->
                val percent = if (total > 0) {
                    ((downloaded * 100 / total).toInt()).coerceIn(0, 100)
                } else {
                    0
                }
                _updateState.value = UpdateState.Downloading(downloaded, total, percent)
            }
            // 下载完成：.part → .apk
            if (!renamePartToApk(partFile, finalFile)) {
                throw IOException("重命名安装包失败：${partFile.absolutePath} → ${finalFile.absolutePath}")
            }
            installApk(context, finalFile)
        } catch (e: DownloadCancelledException) {
            // 用户取消：保留 .part，回到 Available 态，用户可再次点击「立即更新」从片段续传
            Log.d("AppUpdateManager", "下载被用户取消，保留 .part 以便续传：${partFile.absolutePath}")
            _updateState.value = UpdateState.Available(config.version, config.url, config.appName)
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "下载失败: ${e.message}", e)
            _updateState.value = UpdateState.Error("下载失败：${e.message}")
        }
    }

    /**
     * 用户主动取消下载（暂停语义：**保留 .part** 以便后续续传，符合需求中场景 1「取消后再点下载」）。
     *
     * 取消不会删除磁盘片段；再次触发 [startDownload] 时将从已有片段继续。
     * 若产品希望「取消即彻底放弃更新」，可改为在此删除 [updatePartFile]——但当前选择保留以支持续传。
     */
    fun cancelDownload() {
        Log.d("AppUpdateManager", "用户取消下载，保留 .part 以便后续续传")
        cancelRequested.set(true)
        currentCall?.cancel()
    }

    /**
     * 通过 [OkHttpClient] 流式下载 APK 到 [partFile]，并在下载过程中持续回调进度。
     *
     * 断点续传核心逻辑：
     *   1. 若 [partFile] 已存在且 size > 0，以该大小为偏移量追加 `Range: bytes=existing-` 请求头；
     *   2. 服务端返回 **206** 且 Content-Range 的 start 与 existing 一致 → **追加写**；
     *      服务端返回 **200**（不支持 Range）或不匹配 → **删除已有片段、从头覆盖写**；
     *   3. totalBytes 在 206 时取 Content-Range 解析出的完整大小，否则取 Content-Length（可能为 -1）。
     *
     * @param url        下载地址
     * @param partFile   下载片段文件（文件名带版本）
     * @param onProgress 进度回调，参数为 (已下载字节数, 总字节数)；
     *                   总字节数可能为 -1（服务端未返回 Content-Length）。
     */
    private suspend fun downloadApk(
        url: String,
        partFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            // 竞态保护：若已取消，直接退出
            if (cancelRequested.get()) throw DownloadCancelledException()

            partFile.parentFile?.mkdirs()

            // 续传判定：片段存在且 > 0 → 带 Range 头请求
            val existing = if (partFile.exists() && partFile.length() > 0) partFile.length() else 0L
            Log.d("AppUpdateManager", "开始下载：url=$url, existing=$existing, partExists=${partFile.exists()}")

            val requestBuilder = Request.Builder().url(url)
            if (existing > 0) {
                requestBuilder.header("Range", "bytes=$existing-")
            }
            currentCall = httpClient.newCall(requestBuilder.build())
            currentCall!!.execute().use { response ->
                val code = response.code
                // 仅 200 / 206 视为成功；其余（4xx/5xx）抛错
                if (code != 200 && code != 206) {
                    throw IOException("下载失败：HTTP $code ${response.message}")
                }
                val body = response.body ?: throw IOException("下载失败：响应体为空")

                val isPartial = (code == 206)
                val rangeInfo = parseContentRange(response.header("Content-Range"))

                // 是否可安全追加：
                //  - 206 且 Content-Range.start == existing → 追加
                //  - 其它（200 或不匹配 Range）→ 从头覆盖，先删已有片段
                val append = isPartial && rangeInfo != null && rangeInfo.start == existing
                if (!append && existing > 0) {
                    Log.w("AppUpdateManager", "服务端不支持断点续传（code=$code），删除已有 .part，从头完整写入")
                    partFile.delete()
                }

                val effectiveExisting = if (append) existing else 0L
                val totalBytes = if (append && rangeInfo != null) {
                    rangeInfo.total // 完整文件大小
                } else {
                    body.contentLength() // 完整长度（-1 表示未知）
                }

                Log.d("AppUpdateManager", "下载分支：isPartial=$isPartial, append=$append, totalBytes=$totalBytes")

                body.byteStream().use { input ->
                    val output = FileOutputStream(partFile, append)
                    try {
                        val buffer = ByteArray(8 * 1024)
                        var readCount = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (cancelRequested.get()) throw DownloadCancelledException()
                            output.write(buffer, 0, read)
                            readCount += read
                            onProgress(effectiveExisting + readCount, totalBytes)
                        }
                        output.flush()
                    } catch (e: IOException) {
                        // currentCall.cancel() 在取消时会抛出 IOException，统一转成取消异常
                        if (cancelRequested.get()) throw DownloadCancelledException()
                        throw e
                    } finally {
                        output.close()
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

    /**
     * 下载中的临时片段文件（文件名带版本，避免不同版本残片互相污染）。
     * 路径位于 getExternalFilesDir(null)/apk/，与 file_paths.xml 的 external-files-path 对应。
     */
    private fun updatePartFile(context: Context, version: String): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(File(root, APK_DIR), "${PART_FILE_PREFIX}${version}${PART_FILE_SUFFIX}")
    }

    /**
     * 下载完成后 rename 得到的最终 APK 文件（与 [updatePartFile] 同目录）。
     */
    private fun updateApkFile(context: Context, version: String): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(File(root, APK_DIR), "app_update_${version}.apk")
    }

    /**
     * 开始新版本下载前，清理**其它版本**的 .part 残片（文件名前缀匹配）。
     * 当前版本的片段不删除，以便续传。
     */
    private fun cleanupStalePartFiles(context: Context, currentVersion: String) {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(root, APK_DIR)
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { f ->
            val name = f.name
            if (name.startsWith(PART_FILE_PREFIX) && name.endsWith(PART_FILE_SUFFIX)) {
                val versionPart = name.substring(
                    PART_FILE_PREFIX.length,
                    name.length - PART_FILE_SUFFIX.length
                )
                if (versionPart != currentVersion) {
                    val deleted = f.delete()
                    Log.d("AppUpdateManager", "清理旧版本残片：$name deleted=$deleted")
                }
            }
        }
    }

    /**
     * 将下载完成的 .part 重命名为最终 .apk。
     */
    private fun renamePartToApk(partFile: File, finalFile: File): Boolean {
        if (!partFile.exists()) return false
        finalFile.parentFile?.mkdirs()
        if (finalFile.exists()) finalFile.delete()
        return partFile.renameTo(finalFile)
    }

    /**
     * 解析 `Content-Range` 头（形如 `bytes 1000-1999/5000`）。
     */
    private fun parseContentRange(header: String?): ContentRangeInfo? {
        if (header.isNullOrBlank()) return null
        val matcher = CONTENT_RANGE_PATTERN.matcher(header.trim())
        if (!matcher.find()) return null
        return try {
            ContentRangeInfo(
                start = matcher.group(1).toLong(),
                end = matcher.group(2).toLong(),
                total = matcher.group(3).toLong()
            )
        } catch (e: NumberFormatException) {
            null
        }
    }

    companion object {
        /** APK 落盘子目录（与 file_paths.xml 的 external-files-path path=apk/ 对应） */
        private const val APK_DIR = "apk"

        /** 下载片段文件名前缀（含版本，避免不同版本残片互相污染） */
        private const val PART_FILE_PREFIX = "app_update_"

        /** 下载片段文件名后缀 */
        private const val PART_FILE_SUFFIX = ".apk.part"

        /** 匹配 Content-Range：bytes <start>-<end>/<total> */
        private val CONTENT_RANGE_PATTERN =
            Pattern.compile("bytes\\s+(\\d+)\\-(\\d+)/(\\d+)", Pattern.CASE_INSENSITIVE)
    }
}

/**
 * 取消下载时抛出的内部异常（不视为失败，保留 .part 以便续传）。
 */
private class DownloadCancelledException : Exception("下载已取消")

/**
 * Content-Range 解析结果。
 */
private data class ContentRangeInfo(
    val start: Long,
    val end: Long,
    val total: Long
)

/**
 * 最新版本配置（缓存用）。
 */
data class AppConfig(
    val version: String,
    val url: String,
    val appName: String
)

/**
 * [checkUpdate] 的返回结果（诊断用）。
 *
 * @param hasUpdate 是否检测到新版本
 * @param isError  是否发生请求 / 接口异常
 * @param message  诊断信息 / 远端版本号 / 错误描述
 */
data class CheckResult(
    val hasUpdate: Boolean = false,
    val isError: Boolean = false,
    val message: String = ""
)

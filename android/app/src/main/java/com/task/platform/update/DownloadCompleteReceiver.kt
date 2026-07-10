package com.task.platform.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 下载完成广播接收器
 *
 * 监听 [DownloadManager.ACTION_DOWNLOAD_COMPLETE]：APK 下载完成后，
 * 通过 Hilt EntryPoint 取得 [AppUpdateManager] 并调用 [AppUpdateManager.onDownloadComplete]
 * 触发系统安装器安装。
 *
 * 在 AndroidManifest 中以 <receiver> 静态注册（exported=false），仅接收本 App 的下载完成事件。
 */
class DownloadCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        Log.d("DownloadCompleteReceiver", "收到下载完成广播 downloadId=$downloadId")

        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                AppUpdateManagerEntryPoint::class.java
            )
            entryPoint.appUpdateManager().onDownloadComplete(context, downloadId)
        } catch (e: Exception) {
            Log.e("DownloadCompleteReceiver", "处理下载完成事件失败: ${e.message}", e)
            Toast.makeText(context, "更新处理失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Hilt EntryPoint：供 BroadcastReceiver 获取单例 [AppUpdateManager]。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppUpdateManagerEntryPoint {
    fun appUpdateManager(): AppUpdateManager
}

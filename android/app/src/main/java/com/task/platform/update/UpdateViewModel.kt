package com.task.platform.update

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.task.platform.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 版本更新 ViewModel（Hilt）
 *
 * 持有 [AppUpdateManager] 的状态与能力，供 Compose UI 通过 [updateState] 观察、
 * 通过 [checkUpdate] / [startDownload] / [dismiss] 交互。
 *
 * 注：状态 StateFlow 由 [AppUpdateManager]（单例）持有，下载与安装逻辑统一写入同一 StateFlow，
 * UI 通过观察它刷新。
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    /** UI 状态（透传自 AppUpdateManager） */
    val updateState = appUpdateManager.updateState

    /** App 启动后检查一次更新（开机自动，受进程级去重约束） */
    fun checkUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            appUpdateManager.checkUpdate(isAuto = true)
        }
    }

    /** 立即更新（下载并安装，AppUpdateManager.startDownload 为挂起函数，需 IO 协程） */
    fun startDownload(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            appUpdateManager.startDownload(context)
        }
    }

    /** 取消下载（暂停语义：保留 .part，可再次点击「立即更新」从片段续传） */
    fun cancelDownload() {
        appUpdateManager.cancelDownload()
    }

    /** 稍后再说 / 关闭弹窗 */
    fun dismiss() {
        appUpdateManager.dismiss()
    }

    /**
     * 手动检查新版本（设置页按钮用）：检查后弹 Toast 反馈结果。
     * 若检测到新版本，不弹 Toast —— TaskNavGraph 根部的 UpdateDialog
     * 会因 state=Available 自动弹出。
     */
    fun manualCheck(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = appUpdateManager.checkUpdate(isAuto = false)
            withContext(Dispatchers.Main) {
                if (result.hasUpdate) {
                    // 不弹 Toast，TaskNavGraph 根部的 UpdateDialog 会因 state=Available 自动弹出
                } else if (result.isError) {
                    Toast.makeText(context, "检查失败：${result.message}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "已是最新版本 v${BuildConfig.VERSION_NAME}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

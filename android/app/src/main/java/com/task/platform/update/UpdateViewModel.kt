package com.task.platform.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 版本更新 ViewModel（Hilt）
 *
 * 持有 [AppUpdateManager] 的状态与能力，供 Compose UI 通过 [updateState] 观察、
 * 通过 [checkUpdate] / [startDownload] / [dismiss] 交互。
 *
 * 注：状态 StateFlow 由 [AppUpdateManager]（单例）持有，因为下载完成广播也会更新同一状态，
 * 保证 UI 与广播处理结果一致。
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    /** UI 状态（透传自 AppUpdateManager） */
    val updateState = appUpdateManager.updateState

    /** App 启动后检查一次更新 */
    fun checkUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            appUpdateManager.checkUpdate()
        }
    }

    /** 立即更新（下载并安装） */
    fun startDownload(context: Context) {
        appUpdateManager.startDownload(context)
    }

    /** 稍后再说 / 关闭弹窗 */
    fun dismiss() {
        appUpdateManager.dismiss()
    }
}

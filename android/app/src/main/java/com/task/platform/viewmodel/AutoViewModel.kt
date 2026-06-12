package com.task.platform.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.task.platform.model.AutoRecord
import com.task.platform.model.AutoTask
import com.task.platform.model.TaskDTO
import com.task.platform.model.UserInfo
import com.task.platform.network.ApiClient
import com.task.platform.service.AutomationService
import com.task.platform.service.AutomationOverlayService
import com.task.platform.storage.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 自动化任务调度 ViewModel
 *
 * 职责：
 * 1. 管理自动化执行的完整生命周期
 * 2. 检查自动模式开关 + 无障碍服务状态
 * 3. 启动/停止 AutomationService
 * 4. 管理 UI 进度状态
 * 5. 加载自动化操作日志
 */
@HiltViewModel
class AutoViewModel @Inject constructor(
    private val application: Application,
    private val dataStoreManager: DataStoreManager
) : AndroidViewModel(application) {

    // ─── UI 状态定义 ───────────────────────────────────────

    /** 自动化执行步骤 */
    data class AutoStep(
        val step: String,       // open_app/search/play_video/like/comment/screenshot
        val label: String,      // 用户可见的描述
        val status: Int,        // 0进行中 1成功 2失败
        val detail: String = "" // 详细信息
    )

    sealed class AutoUiState {
        object Idle : AutoUiState()
        object Preparing : AutoUiState()
        data class Running(
            val currentStep: String,
            val steps: List<AutoStep>
        ) : AutoUiState()
        data class Completed(val success: Boolean, val message: String) : AutoUiState()
        data class Error(val message: String) : AutoUiState()
    }

    private val _autoUiState = MutableStateFlow<AutoUiState>(AutoUiState.Idle)
    val autoUiState: StateFlow<AutoUiState> = _autoUiState.asStateFlow()

    /** 当前步骤列表 */
    private val _steps = MutableStateFlow<List<AutoStep>>(emptyList())
    val steps: StateFlow<List<AutoStep>> = _steps.asStateFlow()

    /** 用户信息（含 autoMode） */
    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    /** 自动化日志记录 */
    private val _autoRecords = MutableStateFlow<List<AutoRecord>>(emptyList())
    val autoRecords: StateFlow<List<AutoRecord>> = _autoRecords.asStateFlow()

    /** 当自动化完成后需要跳转到截图上传页时，发射 taskId（一次性事件） */
    private val _navigateToUpload = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 1)
    val navigateToUpload: SharedFlow<Long> = _navigateToUpload.asSharedFlow()

    init {
        loadUserInfo()
    }

    fun refreshUserInfo() {
        loadUserInfo()
    }

    // ─── 用户信息 ──────────────────────────────────────────

    private fun loadUserInfo() {
        viewModelScope.launch {
            val json = dataStoreManager.getUserInfo().first()
            if (json != null) {
                try {
                    _userInfo.value = Gson().fromJson(json, UserInfo::class.java)
                } catch (_: Exception) { }
            }
        }
    }

    // ─── 自动化核心逻辑 ─────────────────────────────────────

    /**
     * 启动自动化任务
     *
     * @param task 目标任务信息
     * @return null=成功启动，String=错误原因
     */
    fun startAutomation(task: TaskDTO): String? {
        val user = _userInfo.value

        // 1. 检查自动模式是否开启 (autoMode >= 1)
        if (user == null || user.autoMode < 1) {
            return "请先在设置中开启自动化模式"
        }

        // 2. 检查无障碍服务是否已开启
        val ctx = application.applicationContext
        if (!AutomationService.isAccessibilityEnabled(ctx)) {
            return "ACCESSIBILITY_NEEDED" // 特殊标记，UI层引导用户开启
        }

        // 3. 构建 AutoTask 对象
        val autoTask = AutoTask(
            platform = task.platform,
            taskType = task.taskType,
            targetUrl = task.targetUrl,
            requirements = task.requirements,
            taskId = task.id,
            userId = user.id,
            commentCategoryIds = task.commentCategoryIds
        )

        // 4. 重置状态
        _steps.value = emptyList()
        _autoUiState.value = AutoUiState.Preparing

        // 5. 设置回调，监听自动化进度
        AutomationService.onActionResult = { success, message ->
            viewModelScope.launch {
                // 解析步骤消息: "✓ step: action — result" 或 "✗ step: action — result"
                if (message.contains(":")) {
                    val statusChar = message.trim().firstOrNull()
                    val status = when (statusChar) {
                        '✓' -> 1
                        '✗' -> 2
                        else -> 0
                    }
                    val parts = message.trim().drop(2).split(" — ", limit = 2)
                    val stepInfo = if (parts.isNotEmpty()) parts[0].split(": ", limit = 2) else listOf("", message)
                    val stepName = stepInfo.getOrElse(0) { "unknown" }
                    val action = stepInfo.getOrElse(1) { stepInfo.getOrElse(0) { "" } }
                    val result = parts.getOrElse(1) { "" }

                    val step = AutoStep(
                        step = stepName.trim(),
                        label = action.trim(),
                        status = status,
                        detail = result.trim()
                    )
                    val currentSteps = _steps.value.toMutableList()
                    // 更新同一步骤或追加
                    val existingIndex = currentSteps.indexOfFirst { it.step == stepName.trim() }
                    if (existingIndex >= 0) {
                        currentSteps[existingIndex] = step
                    } else {
                        currentSteps.add(step)
                    }
                    _steps.value = currentSteps

                    _autoUiState.value = AutoUiState.Running(
                        currentStep = stepName.trim(),
                        steps = currentSteps
                    )
                }

                // 最终完成
                if (message.contains("自动化任务执行完成") || message.contains("任务完成")) {
                    _autoUiState.value = AutoUiState.Completed(success = true, message = "任务执行完成")

                    // 检查是否需要跳转到截图上传页（automator 在消息中附带 UPLOAD:taskId）
                    val uploadMarker = "UPLOAD:"
                    val uploadIdx = message.indexOf(uploadMarker)
                    if (uploadIdx >= 0) {
                        val taskIdStr = message.substring(uploadIdx + uploadMarker.length)
                            .trim().split(" ").firstOrNull()
                        val taskId = taskIdStr?.toLongOrNull()
                        if (taskId != null) {
                            _navigateToUpload.tryEmit(taskId)
                        }
                    }

                    AutomationService.onActionResult = null
                } else if (message.contains("异常") || message.contains("失败") || message.contains("停止")) {
                    if (!success && !message.contains("STEP:")) {
                        _autoUiState.value = AutoUiState.Completed(success = false, message = message)
                        AutomationService.onActionResult = null
                    }
                }
            }
        }

        // 5.5 在主线程启动悬浮窗（必须在 execute 之前，确保 instance 已设置）
        AutomationOverlayService.show(ctx, task.requirements?.take(15) ?: "自动化任务")

        // 6. 启动自动化服务
        val service = AutomationService.instance
        if (service == null) {
            _autoUiState.value = AutoUiState.Error("无障碍服务未运行，请先开启")
            return "无障碍服务未运行"
        }

        service.execute(autoTask)
        _autoUiState.value = AutoUiState.Running(
            currentStep = "准备中",
            steps = emptyList()
        )

        return null // 成功
    }

    /**
     * 停止自动化任务
     */
    fun stopAutomation() {
        AutomationService.instance?.stop()
        _autoUiState.value = AutoUiState.Completed(success = false, message = "已手动停止")
        AutomationService.onActionResult = null
    }

    /**
     * 重置自动化状态
     */
    fun resetAutoState() {
        _autoUiState.value = AutoUiState.Idle
        _steps.value = emptyList()
        AutomationService.onActionResult = null
    }

    /**
     * 加载自动化操作日志
     */
    fun loadAutoRecords(taskId: Long) {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getAutoRecords(taskId)
                if (response.code == 200 && response.data != null) {
                    _autoRecords.value = response.data
                }
            } catch (e: Exception) {
                android.util.Log.e("AutoViewModel", "加载自动化日志失败", e)
            }
        }
    }

    /**
     * 检查无障碍服务是否已开启
     */
    fun isAccessibilityEnabled(): Boolean {
        return AutomationService.isAccessibilityEnabled(application.applicationContext)
    }

    /**
     * 跳转到无障碍服务设置页
     */
    fun openAccessibilitySettings() {
        AutomationService.openAccessibilitySettings(application.applicationContext)
    }
}

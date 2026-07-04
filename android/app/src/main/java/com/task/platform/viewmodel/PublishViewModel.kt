package com.task.platform.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.task.platform.model.MergeHistoryVO
import com.task.platform.model.PublishMaterialPreviewVO
import com.task.platform.model.PublishTaskDTO
import com.task.platform.network.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 发布任务 ViewModel
 * 管理发布任务列表、领取、完成等操作
 */
@HiltViewModel
class PublishViewModel @Inject constructor() : ViewModel() {

    // =================== UI State ===================

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class TasksLoaded(val tasks: List<PublishTaskDTO>) : UiState()
        data class MyTasksLoaded(val tasks: List<PublishTaskDTO>) : UiState()
        object ClaimSuccess : UiState()
        object CompleteSuccess : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 操作错误事件（一次性消费）
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun clearActionError() {
        _actionError.value = null
    }

    // 全部发布任务列表缓存
    private val _taskList = MutableStateFlow<List<PublishTaskDTO>>(emptyList())
    val taskList: StateFlow<List<PublishTaskDTO>> = _taskList.asStateFlow()

    // 我的发布任务列表缓存
    private val _myTaskList = MutableStateFlow<List<PublishTaskDTO>>(emptyList())
    val myTaskList: StateFlow<List<PublishTaskDTO>> = _myTaskList.asStateFlow()

    // =================== 加载全部发布任务 ===================

    /**
     * 加载全部发布任务列表
     */
    fun loadTasks() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            runCatching {
                val response = ApiClient.apiService.getPublishTasks()
                if (response.code == 200 && response.data != null) {
                    _taskList.value = response.data
                    _uiState.value = UiState.TasksLoaded(response.data)
                } else {
                    _uiState.value = UiState.Error(response.msg ?: "加载发布任务失败")
                }
            }.onFailure {
                _uiState.value = UiState.Error(it.message ?: "网络异常，请稍后重试")
            }
        }
    }

    // =================== 加载我的发布任务 ===================

    /**
     * 加载我的发布任务列表
     */
    fun loadMyTasks() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            runCatching {
                val response = ApiClient.apiService.getMyPublishTasks()
                if (response.code == 200 && response.data != null) {
                    _myTaskList.value = response.data
                    _uiState.value = UiState.MyTasksLoaded(response.data)
                } else {
                    _uiState.value = UiState.Error(response.msg ?: "加载我的发布任务失败")
                }
            }.onFailure {
                _uiState.value = UiState.Error(it.message ?: "网络异常，请稍后重试")
            }
        }
    }

    // =================== 领取任务 ===================

    /**
     * 领取发布任务
     * @param taskId 任务ID
     * @param onSuccess 成功回调
     */
    fun claimTask(taskId: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val response = ApiClient.apiService.claimPublishTask(taskId)
                if (response.code == 200) {
                    _uiState.value = UiState.ClaimSuccess
                    onSuccess()
                } else {
                    _actionError.value = response.msg ?: "领取任务失败"
                }
            }.onFailure {
                _actionError.value = it.message ?: "领取任务失败"
            }
        }
    }

    // =================== 完成任务 ===================

    /**
     * 完成发布任务
     * @param taskId 任务ID
     * @param onSuccess 成功回调
     */
    fun completeTask(taskId: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val response = ApiClient.apiService.completePublishTask(taskId)
                if (response.code == 200) {
                    _uiState.value = UiState.CompleteSuccess
                    onSuccess()
                } else {
                    _actionError.value = response.msg ?: "完成任务失败"
                }
            }.onFailure {
                _actionError.value = it.message ?: "完成任务失败"
            }
        }
    }

    // =================== 重置错误 ===================

    /**
     * 重置错误状态
     */
    fun resetError() {
        if (_uiState.value is UiState.Error) {
            _uiState.value = UiState.Idle
        }
    }

    // =================== 素材预览（随机素材） ===================

    /** 当前随机素材预览结果 */
    private val _materialsPreview = MutableStateFlow<PublishMaterialPreviewVO?>(null)
    val materialsPreview: StateFlow<PublishMaterialPreviewVO?> = _materialsPreview.asStateFlow()

    /** 刷新冷却剩余秒数（0=可刷新） */
    private val _refreshCooldownSeconds = MutableStateFlow(0)
    val refreshCooldownSeconds: StateFlow<Int> = _refreshCooldownSeconds.asStateFlow()

    /** 素材加载错误 */
    private val _materialsError = MutableStateFlow<String?>(null)
    val materialsError: StateFlow<String?> = _materialsError.asStateFlow()

    fun clearMaterialsError() {
        _materialsError.value = null
    }

    /**
     * 加载项目随机素材（首次进入详情时调用）
     */
    fun loadMaterials(projectId: Long) {
        if (projectId <= 0) return
        viewModelScope.launch {
            runCatching {
                val response = ApiClient.apiService.getPublishMaterials(projectId)
                if (response.code == 200 && response.data != null) {
                    _materialsPreview.value = response.data
                    _materialsError.value = null
                } else {
                    _materialsError.value = response.msg ?: "加载素材失败"
                }
            }.onFailure {
                _materialsError.value = it.message ?: "网络异常"
            }
        }
    }

    /**
     * 刷新素材（60 秒冷却，由服务端管控实际随机逻辑）
     */
    fun refreshMaterials(projectId: Long) {
        if (_refreshCooldownSeconds.value > 0) return
        if (projectId <= 0) return
        viewModelScope.launch {
            runCatching {
                val response = ApiClient.apiService.getPublishMaterials(projectId)
                if (response.code == 200 && response.data != null) {
                    _materialsPreview.value = response.data
                    _materialsError.value = null
                    _refreshCooldownSeconds.value = 60
                    startCooldownTimer()
                } else {
                    _materialsError.value = response.msg ?: "刷新素材失败"
                }
            }.onFailure {
                _materialsError.value = it.message ?: "网络异常"
            }
        }
    }

    /**
     * 60 秒冷却倒计时
     */
    private fun startCooldownTimer() {
        viewModelScope.launch {
            while (_refreshCooldownSeconds.value > 0) {
                delay(1_000L)
                _refreshCooldownSeconds.value = _refreshCooldownSeconds.value - 1
            }
        }
    }

    // =================== 视频合并预览 ===================

    /** 合并状态 */
    private val _mergeState = MutableStateFlow<MergeState>(MergeState.Idle)
    val mergeState: StateFlow<MergeState> = _mergeState.asStateFlow()

    sealed class MergeState {
        object Idle : MergeState()
        object Merging : MergeState()
        data class Success(val url: String?, val historyId: Long? = null) : MergeState()
        data class Error(val message: String) : MergeState()
    }

    fun mergeVideos(projectId: Long, videoIds: List<Long>? = null, musicId: Long? = null,
                    transition: String = "none", transitionDuration: Double = 0.5,
                    fadeInOut: Boolean = false, subtitle: String = "") {
        if (_mergeState.value is MergeState.Merging) return
        viewModelScope.launch {
            _mergeState.value = MergeState.Merging
            runCatching {
                val req = mutableMapOf<String, Any>()
                req["projectId"] = projectId
                if (musicId != null) req["musicId"] = musicId
                if (!videoIds.isNullOrEmpty()) req["videoIds"] = videoIds
                if (transition != "none") {
                    req["transition"] = transition
                    req["transitionDuration"] = transitionDuration
                }
                if (fadeInOut) req["fadeInOut"] = true
                if (subtitle.isNotBlank()) req["subtitle"] = subtitle
                val response = ApiClient.apiService.mergePublishVideos(req)
                if (response.code == 200 && response.data != null) {
                    _mergeState.value = MergeState.Success(response.data.url, response.data.historyId)
                } else {
                    _mergeState.value = MergeState.Error(response.msg ?: "合并失败")
                }
            }.onFailure {
                _mergeState.value = MergeState.Error(it.message ?: "网络异常")
            }
        }
    }

    fun resetMergeState() {
        _mergeState.value = MergeState.Idle
    }

    // =================== 合并历史 ===================

    private val _mergeHistory = MutableStateFlow<List<MergeHistoryVO>>(emptyList())
    val mergeHistory: StateFlow<List<MergeHistoryVO>> = _mergeHistory.asStateFlow()

    fun loadMergeHistory(projectId: Long) {
        viewModelScope.launch {
            runCatching {
                val response = ApiClient.apiService.getMergeHistory(projectId)
                if (response.code == 200) {
                    _mergeHistory.value = response.data ?: emptyList()
                }
            }
        }
    }
}

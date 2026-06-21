package com.task.platform.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.task.platform.model.PublishTaskDTO
import com.task.platform.network.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
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
}

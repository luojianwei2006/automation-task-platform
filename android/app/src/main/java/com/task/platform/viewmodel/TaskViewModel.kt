package com.task.platform.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.task.platform.model.EarningsSummary
import com.task.platform.model.TaskDTO
import com.task.platform.model.TaskRecordDTO
import com.task.platform.model.UserInfo
import com.task.platform.network.ApiClient
import com.task.platform.repository.TaskRepository
import com.task.platform.storage.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.task.platform.utils.LocationHelper

/**
 * 任务大厅 + 任务详情 + 我的任务 ViewModel
 */
@HiltViewModel
class TaskViewModel @Inject constructor(
    private val application: Application,
    private val taskRepository: TaskRepository,
    private val dataStoreManager: DataStoreManager
) : AndroidViewModel(application) {

    // =================== UI State ===================

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class TaskListLoaded(
            val tasks: List<TaskDTO>,
            val total: Long
        ) : UiState()
        data class TaskDetailLoaded(val task: TaskDTO) : UiState()
        data class MyTasksLoaded(
            val tasks: List<TaskDTO>,
            val total: Long
        ) : UiState()
        data class TaskRecordLoaded(val record: TaskRecordDTO?) : UiState()
        object SubmitSuccess : UiState()
        data class Submitting(val progress: String) : UiState()
        object TaskTimeout : UiState()
        data class TaskApproved(val reward: Double) : UiState()
        data class TaskRejected(val reason: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 操作错误事件（一次性消费，用于弹对话框而不破坏当前页面状态）
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun clearActionError() { _actionError.value = null }

    // 任务列表（缓存）
    private val _taskList = MutableStateFlow<List<TaskDTO>>(emptyList())
    val taskList: StateFlow<List<TaskDTO>> = _taskList.asStateFlow()

    // 我的任务列表（缓存）
    private val _myTaskList = MutableStateFlow<List<TaskDTO>>(emptyList())
    val myTaskList: StateFlow<List<TaskDTO>> = _myTaskList.asStateFlow()

    // 当前任务详情
    private val _currentTask = MutableStateFlow<TaskDTO?>(null)
    val currentTask: StateFlow<TaskDTO?> = _currentTask.asStateFlow()

    // 用户信息
    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    // 收益概览
    private val _earningsSummary = MutableStateFlow<EarningsSummary?>(null)
    val earningsSummary: StateFlow<EarningsSummary?> = _earningsSummary.asStateFlow()

    // 加载任务列表 Job（用于取消旧请求）
    private var loadTasksJob: Job? = null

    // =================== 用户信息 ===================

    /**
     * 加载用户信息
     * 优先从 DataStore 缓存读取，同时尝试从 API 刷新
     */
    fun loadUserInfo() {
        // 先从本地缓存读取
        viewModelScope.launch {
            val json = dataStoreManager.getUserInfo().first()
            if (json != null) {
                try {
                    _userInfo.value = Gson().fromJson(json, UserInfo::class.java)
                } catch (_: Exception) { }
            }
        }

        // 从 API 刷新
        viewModelScope.launch {
            runCatching {
                val response = ApiClient.apiService.getUserProfile()
                if (response.code == 200 && response.data != null) {
                    val profile = response.data
                    val updated = _userInfo.value?.copy(
                        nickname = profile["nickname"] as? String ?: _userInfo.value?.nickname,
                        avatarUrl = profile["avatarUrl"] as? String ?: _userInfo.value?.avatarUrl
                    ) ?: UserInfo(
                        id = (profile["id"] as? Double)?.toLong() ?: 0L,
                        phone = profile["phone"] as? String ?: "",
                        nickname = profile["nickname"] as? String,
                        avatarUrl = profile["avatarUrl"] as? String,
                        realAuthStatus = (profile["realAuthStatus"] as? Double)?.toInt() ?: 0,
                        inviteCode = profile["inviteCode"] as? String
                    )
                    _userInfo.value = updated
                    dataStoreManager.saveUserInfo(Gson().toJson(updated))
                }
            }
        }
    }

    /**
     * 加载收益概览
     */
    fun loadEarningsSummary() {
        viewModelScope.launch {
            runCatching {
                val response = ApiClient.apiService.getEarningsSummary()
                if (response.code == 200) {
                    _earningsSummary.value = response.data
                }
            }
        }
    }

    // =================== 任务列表 ===================

    /**
     * 加载任务列表
     * @param platform 平台筛选（1抖音 2小红书，null=全部）
     * @param type 任务类型筛选（1点赞 2评论，null=全部）
     * @param page 页码
     * @param size 每页数量
     */
    fun loadTasks(
        platform: Int? = null,
        type: Int? = null,
        page: Int = 1,
        size: Int = 20
    ) {
        loadTasksJob?.cancel()
        loadTasksJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            taskRepository.getTasks(platform, type, page, size)
                .onSuccess { pageResponse ->
                    _taskList.value = pageResponse.records
                    _uiState.value = UiState.TaskListLoaded(
                        tasks = pageResponse.records,
                        total = pageResponse.total
                    )
                }
                .onFailure {
                    _uiState.value = UiState.Error(it.message ?: "加载任务列表失败")
                }
        }
    }

    // =================== 任务详情 ===================

    // 用于跟踪两个并发加载是否都完成
    private var taskDetailLoaded = false
    private var taskRecordLoaded = false

    /**
     * 重置加载状态标志（每次进入详情页时调用）
     */
    fun resetDetailLoadFlags() {
        taskDetailLoaded = false
        taskRecordLoaded = false
    }

    /**
     * 两个加载都完成后才切换到 TaskDetailLoaded，避免竞态覆盖
     */
    private fun tryEmitTaskDetailLoaded() {
        if (taskDetailLoaded && taskRecordLoaded) {
            val task = _currentTask.value
            if (task != null) {
                _uiState.value = UiState.TaskDetailLoaded(task)
            }
        }
    }

    /**
     * 加载任务详情
     */
    fun loadTaskDetail(taskId: Long) {
        viewModelScope.launch {
            // 仅首次加载显示 Loading，刷新时保持当前页面不闪白
            if (_uiState.value !is UiState.TaskDetailLoaded) {
                _uiState.value = UiState.Loading
            }
            taskRepository.getTaskDetail(taskId)
                .onSuccess { task ->
                    if (task != null) {
                        _currentTask.value = task
                        taskDetailLoaded = true
                        tryEmitTaskDetailLoaded()
                    } else {
                        _uiState.value = UiState.Error("任务不存在")
                    }
                }
                .onFailure {
                    _uiState.value = UiState.Error(it.message ?: "加载任务详情失败")
                }
        }
    }

    // =================== 接受任务 ===================

    // 接取任务中的加载状态（独立于 uiState，避免覆盖任务详情页内容）
    private val _isAccepting = MutableStateFlow(false)
    val isAccepting: StateFlow<Boolean> = _isAccepting.asStateFlow()

    /**
     * 接受任务
     * @param taskId 任务ID
     * @param onSuccess 接取成功后的回调（由调用方决定后续行为，如刷新详情/记录、提示用户）
     *
     * 注意：无论成功或失败都会结束 isAccepting 状态；失败信息通过 actionError 暴露给 UI 弹 toast，
     * 绝不静默吞掉异常。
     */
    fun acceptTask(taskId: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isAccepting.value = true
            try {
                taskRepository.acceptTask(taskId)
                    .onSuccess {
                        // 接取成功后回调由调用方决定后续行为（重新加载详情+记录）
                        onSuccess()
                    }
                    .onFailure {
                        // 失败信息通过 actionError 暴露给 UI 弹 toast，绝不静默
                        _actionError.value = it.message ?: "接受任务失败"
                    }
            } finally {
                _isAccepting.value = false
            }
        }
    }

    // =================== 放弃任务 ===================

    /**
     * 放弃任务（进行中 → 放弃）
     */
    fun abandonTask(taskId: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            taskRepository.abandonTask(taskId)
                .onSuccess {
                    // 成功后重新加载详情，记录状态会变为 4（超时/放弃）
                    onSuccess()
                }
                .onFailure {
                    _actionError.value = it.message ?: "放弃任务失败"
                }
        }
    }

    // =================== 提交任务截图 ===================

    /**
     * 提交任务截图
     */
    fun submitTask(taskId: Long, screenshotUrl: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            taskRepository.submitTask(taskId, screenshotUrl, null, null)
                .onSuccess {
                    _uiState.value = UiState.SubmitSuccess
                    onSuccess()
                }
                .onFailure {
                    _uiState.value = UiState.Error(it.message ?: "提交任务失败")
                }
        }
    }

    /**
     * 批量上传 + 定位 + 提交（含进度）
     * 定位失败时使用 null，允许无定位提交
     */
    fun submitTaskWithBatchUpload(taskId: Long, uris: List<Uri>) {
        viewModelScope.launch {
            var lat: Double? = null
            var lng: Double? = null
            try {
                _uiState.value = UiState.Submitting("正在获取定位...")
                val (la, ln) = LocationHelper.getCurrentLocation(getApplication()).first()
                if (!la.isNaN() && !ln.isNaN()) {
                    lat = la
                    lng = ln
                    _uiState.value = UiState.Submitting("定位成功，正在上传...")
                } else {
                    _uiState.value = UiState.Submitting("定位失败，正在上传...")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Submitting("定位失败，正在上传...")
            }

            _uiState.value = UiState.Submitting("正在上传截图并提交...")
            taskRepository.submitTaskWithBatchUpload(taskId, uris, lat, lng)
                .onSuccess {
                    _uiState.value = UiState.SubmitSuccess
                }
                .onFailure {
                    _uiState.value = UiState.Error(it.message ?: "提交任务失败")
                }
        }
    }

    // =================== 我的任务记录 ===================

    /**
     * 加载我的任务记录
     * @param page 页码
     * @param size 每页数量
     */
    fun loadMyTasks(page: Int = 1, size: Int = 20) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            taskRepository.getMyTasks(page, size)
                .onSuccess { pageResponse ->
                    _myTaskList.value = pageResponse.records
                    _uiState.value = UiState.MyTasksLoaded(
                        tasks = pageResponse.records,
                        total = pageResponse.total
                    )
                }
                .onFailure {
                    _uiState.value = UiState.Error(it.message ?: "加载我的任务失败")
                }
        }
    }

    // =================== 任务记录相关 ===================

    /**
     * 当前用户对指定任务的记录
     */
    private val _currentRecord = MutableStateFlow<TaskRecordDTO?>(null)
    val currentRecord: StateFlow<TaskRecordDTO?> = _currentRecord.asStateFlow()

    /**
     * 加载当前用户对指定任务的记录
     * 用于在任务详情页判断任务状态
     */
    fun loadTaskRecord(taskId: Long) {
        viewModelScope.launch {
            taskRepository.getTaskRecord(taskId)
                .onSuccess { record ->
                    _currentRecord.value = record
                    taskRecordLoaded = true
                    // 无论有无记录，都尝试触发详情显示
                    tryEmitTaskDetailLoaded()
                }
                .onFailure {
                    // 无记录（404）属于正常情况
                    _currentRecord.value = null
                    taskRecordLoaded = true
                    tryEmitTaskDetailLoaded()
                }
        }
    }

    /**
     * 加载任务记录详情
     */
    fun loadTaskRecordDetail(recordId: Long) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            taskRepository.getTaskRecordDetail(recordId)
                .onSuccess { record ->
                    if (record != null) {
                        _currentRecord.value = record
                        _uiState.value = UiState.TaskRecordLoaded(record)
                    } else {
                        _uiState.value = UiState.Error("记录不存在")
                    }
                }
                .onFailure {
                    _uiState.value = UiState.Error(it.message ?: "加载记录详情失败")
                }
        }
    }

    // =================== 定位辅助 ===================

    /**
     * 检查定位并在范围内时执行操作
     * @param task 任务信息（包含定位要求）
     * @param onWithinRange 在范围内（50米）的回调
     * @param onOutOfRange 不在范围内的回调（需要导航）
     */
    fun checkLocationAndProceed(
        task: TaskDTO,
        onWithinRange: () -> Unit,
        onOutOfRange: () -> Unit
    ) {
        viewModelScope.launch {
            LocationHelper.getCurrentLocation(getApplication()).collect { (lat, lng) ->
                val distance = LocationHelper.calculateDistance(
                    lat, lng,
                    task.locationLat ?: 0.0,
                    task.locationLng ?: 0.0
                )
                if (distance <= 50.0) {
                    onWithinRange()
                } else {
                    onOutOfRange()
                }
            }
        }
    }

    /**
     * 重置错误状态
     */
    fun resetError() {
        if (_uiState.value is UiState.Error) {
            _uiState.value = UiState.Idle
        }
    }
}

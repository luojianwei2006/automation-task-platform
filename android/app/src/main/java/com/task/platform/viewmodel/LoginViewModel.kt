package com.task.platform.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.task.platform.model.LoginResponse
import com.task.platform.network.ApiClient
import com.task.platform.repository.AuthRepository
import com.task.platform.storage.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 登录/注册 ViewModel
 * 管理：登录表单状态、短信倒计时、网络请求状态
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    // ==================== UI State ====================

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class Success(val response: LoginResponse) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 记住密码：保存的账号密码
    private val _savedPhone = MutableStateFlow<String?>(null)
    val savedPhone: StateFlow<String?> = _savedPhone.asStateFlow()

    private val _savedPassword = MutableStateFlow<String?>(null)
    val savedPassword: StateFlow<String?> = _savedPassword.asStateFlow()

    // 全局开关：注册/登录是否需要短信验证码（来自 /api/user/config 的 require_phone_verify）
    private val _requirePhoneVerify = MutableStateFlow(true)
    val requirePhoneVerify: StateFlow<Boolean> = _requirePhoneVerify.asStateFlow()

    init {
        // 加载保存的账号密码
        viewModelScope.launch {
            dataStoreManager.getSavedPhone().collect { _savedPhone.value = it }
        }
        viewModelScope.launch {
            dataStoreManager.getSavedPassword().collect { _savedPassword.value = it }
        }
        // 订阅「是否需要短信验证码」开关，驱动 UI 显隐与校验放宽
        viewModelScope.launch {
            dataStoreManager.getRequirePhoneVerify().collect { _requirePhoneVerify.value = it }
        }
    }

    // 短信验证码倒计时（秒）
    private val _countdown = MutableStateFlow(0)
    val countdown: StateFlow<Int> = _countdown.asStateFlow()

    private var countdownJob: Job? = null

    // ==================== 短信验证码 ====================

    /**
     * 发送验证码
     */
    fun sendSmsCode(phone: String, type: Int = 2) {
        if (phone.length != 11) {
            _uiState.value = UiState.Error("请输入正确的11位手机号")
            return
        }
        if (_countdown.value > 0) {
            _uiState.value = UiState.Error("请等待 ${_countdown.value}s 后重新发送")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            authRepository.sendSmsCode(phone, type)
                .onSuccess {
                    _uiState.value = UiState.Idle
                    startCountdown()
                }
                .onFailure {
                    _uiState.value = UiState.Error(it.message ?: "发送失败")
                }
        }
    }

    /**
     * 开始60秒倒计时
     */
    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            _countdown.value = 60
            while (_countdown.value > 0) {
                delay(1000)
                _countdown.value -= 1
            }
        }
    }

    // ==================== 密码登录 ====================

    fun loginWithPassword(phone: String, password: String) {
        if (!validatePhone(phone)) return
        if (password.isBlank()) {
            _uiState.value = UiState.Error("密码不能为空")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            authRepository.loginWithPassword(phone, password)
                .onSuccess { loginResponse ->
                    // 持久化 Token 和用户信息
                    dataStoreManager.saveToken(loginResponse.token, loginResponse.refreshToken)
                    dataStoreManager.saveUserInfo(Gson().toJson(loginResponse.userInfo))
                    // 保存登录账号和密码，下次自动填充
                    dataStoreManager.saveLoginCredentials(phone, password)
                    // 将 Token 注入到 ApiClient 拦截器，后续请求自动带 Authorization
                    ApiClient.setToken(loginResponse.token)
                    _uiState.value = UiState.Success(loginResponse)
                }
                .onFailure {
                    _uiState.value = UiState.Error(it.message ?: "登录失败")
                }
        }
    }

    // ==================== 验证码登录 ====================

    fun loginWithSms(phone: String, code: String) {
        if (!validatePhone(phone)) return
        if (code.length != 6) {
            _uiState.value = UiState.Error("请输入6位验证码")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            authRepository.loginWithSms(phone, code)
                .onSuccess { loginResponse ->
                    dataStoreManager.saveToken(loginResponse.token, loginResponse.refreshToken)
                    dataStoreManager.saveUserInfo(Gson().toJson(loginResponse.userInfo))
                    // 将 Token 注入到 ApiClient 拦截器
                    ApiClient.setToken(loginResponse.token)
                    _uiState.value = UiState.Success(loginResponse)
                }
                .onFailure {
                    _uiState.value = UiState.Error(it.message ?: "登录失败")
                }
        }
    }

    // ==================== 注册 ====================

    fun register(
        phone: String,
        code: String,
        password: String,
        confirmPassword: String,
        nickname: String,
        inviteCode: String?
    ) {
        if (!validatePhone(phone)) return
        // 仅当全局开关要求短信验证时，才强制 6 位验证码（开关关闭时后端接受空验证码）
        if (_requirePhoneVerify.value && code.length != 6) {
            _uiState.value = UiState.Error("请输入6位验证码")
            return
        }
        if (password.length < 6) {
            _uiState.value = UiState.Error("密码至少6位")
            return
        }
        if (password != confirmPassword) {
            _uiState.value = UiState.Error("两次密码输入不一致")
            return
        }
        if (nickname.isBlank()) {
            _uiState.value = UiState.Error("昵称不能为空")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            authRepository.register(phone, code, password, nickname, inviteCode)
                .onSuccess { loginResponse ->
                    dataStoreManager.saveToken(loginResponse.token, loginResponse.refreshToken)
                    dataStoreManager.saveUserInfo(Gson().toJson(loginResponse.userInfo))
                    // 将 Token 注入到 ApiClient 拦截器
                    ApiClient.setToken(loginResponse.token)
                    _uiState.value = UiState.Success(loginResponse)
                }
                .onFailure {
                    _uiState.value = UiState.Error(it.message ?: "注册失败")
                }
        }
    }

    fun resetError() {
        if (_uiState.value is UiState.Error) {
            _uiState.value = UiState.Idle
        }
    }

    /** 登录/注册成功或失败后，重置 UI 状态，防止 LaunchedEffect 重复触发 */
    fun resetUiState() {
        _uiState.value = UiState.Idle
    }

    private fun validatePhone(phone: String): Boolean {
        return if (phone.length != 11 || !phone.startsWith("1")) {
            _uiState.value = UiState.Error("请输入正确的手机号")
            false
        } else true
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}

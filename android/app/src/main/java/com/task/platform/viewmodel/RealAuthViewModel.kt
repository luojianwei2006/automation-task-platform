package com.task.platform.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.task.platform.repository.UserRepository
import com.task.platform.service.RealAuthStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 实名认证 ViewModel
 */
@HiltViewModel
class RealAuthViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    sealed class UiState {
        data object Loading : UiState()
        /** 未认证 or 失败 → 展示表单 */
        data class Form(val failReason: String? = null) : UiState()
        data object Pending : UiState()  // 审核中
        data object Passed : UiState()   // 已认证
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    init {
        loadAuthStatus()
    }

    private fun loadAuthStatus() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            userRepository.getRealAuthStatus()
                .onSuccess { status ->
                    _uiState.value = when (status.status) {
                        1 -> UiState.Pending
                        2 -> UiState.Passed
                        3 -> UiState.Form(failReason = status.statusDesc)
                        else -> UiState.Form() // 0=未认证
                    }
                }
                .onFailure {
                    _uiState.value = UiState.Error(it.message ?: "加载失败")
                }
        }
    }

    fun submitRealAuth(
        realName: String,
        idCard: String,
        idCardFrontUrl: String,
        idCardBackUrl: String,
        holdIdCardUrl: String? = null
    ) {
        if (realName.isBlank()) {
            _uiState.value = UiState.Error("真实姓名不能为空")
            return
        }
        if (idCard.length != 18) {
            _uiState.value = UiState.Error("请输入18位身份证号码")
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            userRepository.submitRealAuth(realName, idCard, idCardFrontUrl, idCardBackUrl, holdIdCardUrl)
                .onSuccess {
                    _uiState.value = UiState.Pending
                }
                .onFailure {
                    _uiState.value = UiState.Error(it.message ?: "提交失败，请稍后重试")
                }
            _isSubmitting.value = false
        }
    }

    fun resetError() {
        if (_uiState.value is UiState.Error) {
            loadAuthStatus()
        }
    }
}

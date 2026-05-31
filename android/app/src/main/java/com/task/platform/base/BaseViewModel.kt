package com.task.platform.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 基础 ViewModel - 提供通用功能
 * 
 * 所有 ViewModel 应继承此类
 */
abstract class BaseViewModel : ViewModel() {

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    /**
     * 启动协程（自动处理加载状态）
     */
    protected fun launchWithLoading(
        showLoading: Boolean = true,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (showLoading) {
                    _isLoading.value = true
                }
                block()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "操作失败"
            } finally {
                if (showLoading) {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * 清除错误消息
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * 显示错误消息
     */
    fun showErrorMessage(message: String) {
        _errorMessage.value = message
    }
}

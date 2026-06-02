package com.task.platform.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.task.platform.model.WithdrawRecord
import com.task.platform.network.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WithdrawViewModel @Inject constructor() : ViewModel() {

    private val _records = MutableStateFlow<List<WithdrawRecord>>(emptyList())
    val records: StateFlow<List<WithdrawRecord>> = _records.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _applySuccess = MutableStateFlow(false)
    val applySuccess: StateFlow<Boolean> = _applySuccess.asStateFlow()

    fun applyWithdraw(amount: Double, method: String, account: String, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            kotlin.runCatching {
                val body = mapOf<String, Any>(
                    "amount" to amount,
                    "method" to method,
                    "account" to account
                )
                val resp = ApiClient.apiService.applyWithdraw(body)
                if (resp.code == 200) {
                    _applySuccess.value = true
                    callback(true, "提现申请已提交")
                } else {
                    callback(false, resp.msg ?: "申请失败")
                }
            }.onFailure {
                callback(false, it.message ?: "网络错误")
            }
            _isLoading.value = false
        }
    }

    fun loadRecords() {
        viewModelScope.launch {
            kotlin.runCatching {
                val resp = ApiClient.apiService.getWithdrawRecords()
                if (resp.code == 200 && resp.data != null) {
                    _records.value = resp.data!!
                }
            }
        }
    }

    fun resetApplySuccess() { _applySuccess.value = false }
}

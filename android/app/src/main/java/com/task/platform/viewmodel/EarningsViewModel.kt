package com.task.platform.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.task.platform.model.EarningsRecord
import com.task.platform.model.EarningsSummary
import com.task.platform.model.UserInfo
import com.task.platform.network.ApiClient
import com.task.platform.storage.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 收益中心 ViewModel
 */
@HiltViewModel
class EarningsViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _earningsSummary = MutableStateFlow<EarningsSummary?>(null)
    val earningsSummary: StateFlow<EarningsSummary?> = _earningsSummary.asStateFlow()

    private val _earningsRecords = MutableStateFlow<List<EarningsRecord>>(emptyList())
    val earningsRecords: StateFlow<List<EarningsRecord>> = _earningsRecords.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 当前筛选类型：null=全部, 1=任务收益, 2=邀请奖励, 4=其他 */
    private var currentType: Int? = null
    private var currentPage = 1
    private var hasMore = true

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

    /**
     * 加载收益明细记录
     * @param type 筛选类型（null=全部）
     * @param refresh 是否刷新（重置分页）
     */
    fun loadEarningsRecords(type: Int? = null, refresh: Boolean = true) {
        if (_isLoading.value) return

        if (refresh) {
            currentPage = 1
            hasMore = true
            currentType = type
        }

        if (!hasMore && !refresh) return

        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                val response = ApiClient.apiService.getEarningsRecords(
                    type = currentType,
                    page = currentPage,
                    size = 20
                )
                if (response.code == 200 && response.data != null) {
                    val newRecords = response.data.records
                    if (refresh) {
                        _earningsRecords.value = newRecords
                    } else {
                        _earningsRecords.value = _earningsRecords.value + newRecords
                    }
                    hasMore = newRecords.size >= 20
                    currentPage++
                } else {
                    _errorMessage.value = response.msg ?: "加载收益记录失败"
                }
            }.onFailure {
                _errorMessage.value = it.message ?: "网络错误"
            }
            _isLoading.value = false
        }
    }

    /**
     * 加载更多记录
     */
    fun loadMore() {
        loadEarningsRecords(refresh = false)
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }
}

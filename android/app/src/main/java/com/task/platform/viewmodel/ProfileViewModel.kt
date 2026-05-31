package com.task.platform.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.task.platform.model.UserInfo
import com.task.platform.network.ApiClient
import com.task.platform.repository.UserRepository
import com.task.platform.storage.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 个人中心 ViewModel
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    /** 实名认证状态: 0未认证 1审核中 2已认证 3失败 */
    private val _realAuthStatus = MutableStateFlow(0)
    val realAuthStatus: StateFlow<Int> = _realAuthStatus.asStateFlow()

    /** 用户余额 */
    private val _balance = MutableStateFlow<Double?>(null)
    val balance: StateFlow<Double?> = _balance.asStateFlow()

    // ==================== 邀请信息 ====================

    /** 邀请弹窗是否显示 */
    private val _showInviteDialog = MutableStateFlow(false)
    val showInviteDialog: StateFlow<Boolean> = _showInviteDialog.asStateFlow()

    /** 邀请码 */
    private val _inviteCode = MutableStateFlow<String?>(null)
    val inviteCode: StateFlow<String?> = _inviteCode.asStateFlow()

    /** 邀请链接 */
    private val _inviteUrl = MutableStateFlow<String?>(null)
    val inviteUrl: StateFlow<String?> = _inviteUrl.asStateFlow()

    /** 邀请海报URL（可选） */
    private val _invitePosterUrl = MutableStateFlow<String?>(null)
    val invitePosterUrl: StateFlow<String?> = _invitePosterUrl.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _logoutSuccess = MutableStateFlow(false)
    val logoutSuccess: StateFlow<Boolean> = _logoutSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * 加载用户资料
     * 优先从 DataStore 缓存读取，同时从 API 刷新
     */
    fun loadProfile() {
        // 先从本地缓存读取
        viewModelScope.launch {
            val json = dataStoreManager.getUserInfo().first()
            if (json != null) {
                try {
                    _userInfo.value = Gson().fromJson(json, UserInfo::class.java)
                    _realAuthStatus.value = _userInfo.value?.realAuthStatus ?: 0
                } catch (_: Exception) { }
            }
        }

        // 从 API 刷新
        viewModelScope.launch {
            _isLoading.value = true
            kotlin.runCatching {
                val response = ApiClient.apiService.getUserProfile()
                if (response.code == 200 && response.data != null) {
                    val profile = response.data
                    val updated = _userInfo.value?.copy(
                        nickname = profile["nickname"] as? String ?: _userInfo.value?.nickname,
                        avatarUrl = profile["avatarUrl"] as? String ?: _userInfo.value?.avatarUrl,
                        realAuthStatus = (profile["realAuthStatus"] as? Double)?.toInt()
                            ?: _userInfo.value?.realAuthStatus ?: 0,
                        inviteCode = profile["inviteCode"] as? String ?: _userInfo.value?.inviteCode,
                        wechatAccount = profile["wechatAccount"] as? String ?: _userInfo.value?.wechatAccount,
                        alipayAccount = profile["alipayAccount"] as? String ?: _userInfo.value?.alipayAccount,
                        wechatQrcode = profile["wechatQrcode"] as? String ?: _userInfo.value?.wechatQrcode,
                        alipayQrcode = profile["alipayQrcode"] as? String ?: _userInfo.value?.alipayQrcode
                    ) ?: UserInfo(
                        id = (profile["id"] as? Double)?.toLong() ?: 0L,
                        phone = profile["phone"] as? String ?: "",
                        nickname = profile["nickname"] as? String,
                        avatarUrl = profile["avatarUrl"] as? String,
                        realAuthStatus = (profile["realAuthStatus"] as? Double)?.toInt() ?: 0,
                        inviteCode = profile["inviteCode"] as? String
                    )
                    _userInfo.value = updated
                    _realAuthStatus.value = updated.realAuthStatus
                    dataStoreManager.saveUserInfo(Gson().toJson(updated))

                    // 解析余额（Gson 将 JSON 数字解析为 LazilyParsedNumber，不是 Double）
                    val bal = profile["balance"]
                    _balance.value = when (bal) {
                        is Double -> bal
                        is Float -> bal.toDouble()
                        is Number -> bal.toDouble()   // 覆盖 Gson 的 LazilyParsedNumber
                        is String -> bal.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                }
            }.onFailure {
                _errorMessage.value = it.message ?: "加载用户信息失败"
            }
            _isLoading.value = false
        }

        // 刷新实名认证状态
        viewModelScope.launch {
            kotlin.runCatching {
                val result = userRepository.getRealAuthStatus()
                result.onSuccess { authStatus ->
                    _realAuthStatus.value = authStatus.status
                }
            }
        }
    }

    /**
     * 退出登录
     * 清除 DataStore + ApiClient Token
     */
    fun logout() {
        viewModelScope.launch {
            kotlin.runCatching {
                dataStoreManager.clearAll()
                ApiClient.clearToken()
                _logoutSuccess.value = true
            }.onFailure {
                _errorMessage.value = it.message ?: "退出登录失败"
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 重置退出登录成功标志
     */
    fun resetLogoutSuccess() {
        _logoutSuccess.value = false
    }

    // ==================== 邀请好友 ====================

    /**
     * 加载邀请信息并弹出邀请弹窗
     */
    fun loadInviteInfo() {
        viewModelScope.launch {
            kotlin.runCatching {
                val response = ApiClient.apiService.getInviteLink()
                if (response.code == 200 && response.data != null) {
                    _inviteCode.value = response.data["inviteCode"] as? String
                    _inviteUrl.value = response.data["inviteUrl"] as? String
                    _invitePosterUrl.value = response.data["posterUrl"] as? String
                    _showInviteDialog.value = true
                }
            }.onFailure {
                _errorMessage.value = it.message ?: "加载邀请信息失败"
            }
        }
    }

    /**
     * 关闭邀请弹窗
     */
    fun dismissInviteDialog() {
        _showInviteDialog.value = false
    }

    // ==================== 设置 ====================

    /**
     * 修改密码
     * callback: (success, message)
     */
    fun changePassword(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String,
        callback: (Boolean, String) -> Unit
    ) {
        if (newPassword != confirmPassword) {
            callback(false, "两次输入的新密码不一致")
            return
        }
        if (newPassword.length < 6) {
            callback(false, "新密码长度不能少于6位")
            return
        }
        viewModelScope.launch {
            kotlin.runCatching {
                val body = mapOf<String, String>(
                    "oldPassword" to oldPassword,
                    "newPassword" to newPassword
                )
                val response = ApiClient.apiService.changePassword(body)
                if (response.code == 200) {
                    callback(true, "密码修改成功")
                } else {
                    callback(false, response.msg ?: "修改失败")
                }
            }.onFailure {
                callback(false, it.message ?: "网络错误")
            }
        }
    }

    // ==================== 编辑资料 ====================

    /**
     * 更新用户资料
     * callback: (success, message)
     */
    fun updateProfile(
        nickname: String,
        avatarUrl: String,
        callback: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            kotlin.runCatching {
                val body = mapOf<String, String?>(
                    "nickname" to nickname,
                    "avatarUrl" to avatarUrl.ifBlank { null }
                )
                val response = ApiClient.apiService.updateProfile(body)
                if (response.code == 200) {
                    loadProfile() // 刷新用户信息
                    callback(true, "更新成功")
                } else {
                    callback(false, response.msg ?: "更新失败")
                }
            }.onFailure {
                callback(false, it.message ?: "网络错误")
            }
        }
    }

    // ==================== 钱包绑定 ====================

    /**
     * 绑定/更新收款账户
     * callback: (success, message)
     */
    fun bindWallet(type: Int, account: String, qrcodeUrl: String = "", callback: (Boolean, String) -> Unit) {
        val hasAccount = account.isNotBlank()
        val hasQrcode = qrcodeUrl.isNotBlank()
        if (!hasAccount && !hasQrcode) {
            callback(false, "请上传收款码或填写账号")
            return
        }
        viewModelScope.launch {
            kotlin.runCatching {
                val body = mutableMapOf<String, String>(
                    "type" to type.toString()
                )
                if (hasAccount) body["account"] = account
                if (hasQrcode) body["qrcodeUrl"] = qrcodeUrl
                val response = ApiClient.apiService.bindWallet(body)
                if (response.code == 200) {
                    // 刷新用户信息（更新 wechatAccount/alipayAccount）
                    kotlin.runCatching {
                        val profileResp = ApiClient.apiService.getUserProfile()
                        if (profileResp.code == 200 && profileResp.data != null) {
                            val profile = profileResp.data
                            val updated = _userInfo.value?.copy(
                                wechatAccount = profile["wechatAccount"] as? String
                                    ?: _userInfo.value?.wechatAccount,
                                alipayAccount = profile["alipayAccount"] as? String
                                    ?: _userInfo.value?.alipayAccount,
                                wechatQrcode = profile["wechatQrcode"] as? String
                                    ?: _userInfo.value?.wechatQrcode,
                                alipayQrcode = profile["alipayQrcode"] as? String
                                    ?: _userInfo.value?.alipayQrcode
                            )
                            if (updated != null) {
                                _userInfo.value = updated
                                dataStoreManager.saveUserInfo(Gson().toJson(updated))
                            }
                        }
                    }
                    callback(true, "绑定成功")
                } else {
                    callback(false, response.msg ?: "绑定失败")
                }
            }.onFailure {
                callback(false, it.message ?: "网络错误")
            }
        }
    }

    /**
     * 解绑/删除收款账户
     * type: 1=微信 2=支付宝
     */
    fun unbindWallet(type: Int, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            kotlin.runCatching {
                val response = ApiClient.apiService.unbindWallet(type)
                if (response.code == 200) {
                    // 刷新用户信息
                    kotlin.runCatching {
                        val profileResp = ApiClient.apiService.getUserProfile()
                        if (profileResp.code == 200 && profileResp.data != null) {
                            val profile = profileResp.data
                            val updated = _userInfo.value?.copy(
                                wechatAccount = if (type == 1) null else _userInfo.value?.wechatAccount,
                                alipayAccount = if (type == 2) null else _userInfo.value?.alipayAccount
                            )
                            if (updated != null) {
                                _userInfo.value = updated
                                dataStoreManager.saveUserInfo(Gson().toJson(updated))
                            }
                        }
                    }
                    callback(true, "已解绑")
                } else {
                    callback(false, response.msg ?: "解绑失败")
                }
            }.onFailure {
                callback(false, it.message ?: "网络错误")
            }
        }
    }
}

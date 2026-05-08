package com.task.platform.repository

import com.task.platform.network.ApiClient
import com.task.platform.service.RealAuthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户数据仓库
 * 封装用户资料、实名认证等网络请求
 */
@Singleton
class UserRepository @Inject constructor() {

    private val apiService = ApiClient.apiService

    /**
     * 获取用户实名认证状态
     */
    suspend fun getRealAuthStatus(): Result<RealAuthStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getRealAuthStatus()
            response.data ?: throw Exception(response.msg)
        }
    }

    /**
     * 提交实名认证
     */
    suspend fun submitRealAuth(
        realName: String,
        idCard: String,
        idCardFrontUrl: String,
        idCardBackUrl: String,
        holdIdCardUrl: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = mutableMapOf(
                "realName" to realName,
                "idCard" to idCard,
                "idCardFrontUrl" to idCardFrontUrl,
                "idCardBackUrl" to idCardBackUrl
            )
            holdIdCardUrl?.let { body["holdIdCardUrl"] = it }

            val response = apiService.submitRealAuth(body)
            if (response.code != 200) {
                throw Exception(response.msg)
            }
        }
    }

    /**
     * 更新用户资料
     */
    suspend fun updateProfile(nickname: String?, avatarUrl: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = mutableMapOf<String, String?>()
                nickname?.let { body["nickname"] = it }
                avatarUrl?.let { body["avatarUrl"] = it }
                val response = apiService.updateProfile(body)
                if (response.code != 200) throw Exception(response.msg)
            }
        }
}

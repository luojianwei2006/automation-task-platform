package com.task.platform.repository

import com.task.platform.model.ApiResponse
import com.task.platform.model.LoginResponse
import com.task.platform.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 认证数据仓库
 * 封装所有认证相关网络请求，提供统一的 Result 包装
 */
@Singleton
class AuthRepository @Inject constructor() {

    private val apiService = ApiClient.apiService

    /**
     * 发送短信验证码
     * @param phone 手机号
     * @param type  1=注册 2=登录 3=重置密码
     */
    suspend fun sendSmsCode(phone: String, type: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = apiService.sendSmsCode(
                    mapOf("phone" to phone, "type" to type)
                )
                if (response.code != 200) {
                    throw Exception(response.msg)
                }
            }
        }

    /**
     * 用户注册
     */
    suspend fun register(
        phone: String,
        code: String,
        password: String,
        nickname: String,
        inviteCode: String?
    ): Result<LoginResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = mutableMapOf(
                "phone" to phone,
                "code" to code,
                "password" to password,
                "nickname" to nickname
            )
            if (!inviteCode.isNullOrBlank()) {
                body["inviteCode"] = inviteCode
            }
            val response = apiService.register(body)
            response.data ?: throw Exception(response.msg)
        }
    }

    /**
     * 密码登录
     */
    suspend fun loginWithPassword(phone: String, password: String): Result<LoginResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = apiService.login(
                    mapOf("phone" to phone, "password" to password)
                )
                response.data ?: throw Exception(response.msg)
            }
        }

    /**
     * 验证码登录
     */
    suspend fun loginWithSms(phone: String, code: String): Result<LoginResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = apiService.loginWithSms(
                    mapOf("phone" to phone, "code" to code)
                )
                response.data ?: throw Exception(response.msg)
            }
        }
}

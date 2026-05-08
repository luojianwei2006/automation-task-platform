package com.task.platform.network

import android.content.Context
import com.task.platform.model.ApiResponse
import com.task.platform.service.RealAuthStatus
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import com.task.platform.model.LoginResponse
import com.task.platform.model.PageResponse
import com.task.platform.model.TaskDTO
import com.task.platform.model.EarningsSummary

/**
 * API接口定义
 * 与后端API一一对应
 */
interface ApiService {

    // ========== 认证模块 ==========

    /** 发送验证码 */
    @POST("api/v1/auth/sms/send")
    suspend fun sendSmsCode(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Void>

    /** 用户注册 */
    @POST("api/v1/auth/register")
    suspend fun register(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<LoginResponse>

    /** 密码登录 */
    @POST("api/v1/auth/login")
    suspend fun login(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<LoginResponse>

    /** 验证码登录 */
    @POST("api/v1/auth/login/sms")
    suspend fun loginWithSms(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<LoginResponse>

    // ========== 用户信息模块（第3-6周新增） ==========

    /** 获取用户实名认证状态 */
    @GET("api/v1/user/real-auth/status")
    suspend fun getRealAuthStatus(): ApiResponse<RealAuthStatus>

    /** 提交实名认证 */
    @POST("api/v1/user/real-auth")
    suspend fun submitRealAuth(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Void>

    /** 更新用户资料 */
    @PUT("api/v1/user/profile")
    suspend fun updateProfile(@Body body: Map<String, @JvmSuppressWildcards String?>): ApiResponse<Void>

    /** 获取邀请链接 */
    @GET("api/v1/user/invite/link")
    suspend fun getInviteLink(): ApiResponse<Map<String, String>>

    // ========== 任务模块 ==========

    /** 任务列表（分页+筛选） */
    @GET("api/v1/tasks")
    suspend fun getTasks(
        @Query("platform") platform: Int?,
        @Query("type") type: Int?,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<PageResponse<TaskDTO>>

    /** 任务详情 */
    @GET("api/v1/tasks/{id}")
    suspend fun getTaskDetail(@Path("id") id: Long): ApiResponse<TaskDTO>

    /** 接受任务 */
    @POST("api/v1/tasks/{id}/accept")
    suspend fun acceptTask(@Path("id") id: Long): ApiResponse<Void>

    // ========== 收益模块 ==========

    /** 收益概览 */
    @GET("api/v1/earnings/summary")
    suspend fun getEarningsSummary(): ApiResponse<EarningsSummary>
}

/**
 * API客户端 - 单例模式
 * 
 * 使用方式：
 *   ApiClient.apiService.login(...)   // 通过 Hilt 注入的 Repository 中使用
 *   ApiClient.setToken(token)         // 登录成功后设置Token
 */
object ApiClient {
    private const val BASE_URL = "https://api.taskplatform.com/"

    private var _instance: ApiService? = null
    private var token: String = ""

    /** 便捷属性：直接访问 ApiService（无 Context 依赖的场景） */
    val apiService: ApiService
        get() {
            if (_instance == null) {
                _instance = createApiWithoutContext()
            }
            return _instance!!
        }

    /**
     * 设置Token（登录后调用）
     */
    fun setToken(t: String) {
        token = t
        _instance = null // 强制重建以应用新Token
    }

    fun clearToken() {
        token = ""
        _instance = null
    }

    private fun createApiWithoutContext(): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                if (token.isNotEmpty()) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

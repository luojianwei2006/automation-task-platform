package com.task.platform.network

import android.content.Context
import com.task.platform.BuildConfig
import com.task.platform.model.ApiResponse
import com.task.platform.model.AutoRecord
import com.task.platform.model.WithdrawRecord
import com.task.platform.model.EarningsRecord
import com.task.platform.model.EarningsSummary
import com.task.platform.model.LoginResponse
import com.task.platform.model.PageResponse
import com.task.platform.model.PublishTaskDTO
import com.task.platform.model.TaskDTO
import com.task.platform.model.TaskRecordDTO
import com.task.platform.model.UploadResult
import com.task.platform.service.RealAuthStatus
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MultipartBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * API接口定义
 * 与后端API一一对应
 */
interface ApiService {

    // ==================== 认证模块 ====================

    /** 发送验证码 */
    @POST("api/user/auth/sms/send")
    suspend fun sendSmsCode(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Void>

    /** 用户注册 */
    @POST("api/user/auth/register")
    suspend fun register(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<LoginResponse>

    /** 密码登录 */
    @POST("api/user/auth/login")
    suspend fun login(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<LoginResponse>

    /** 验证码登录 */
    @POST("api/user/auth/login/sms")
    suspend fun loginWithSms(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<LoginResponse>

    // ==================== 用户信息模块（第3-6周新增） ====================

    /** 获取用户实名认证状态 */
    @GET("api/user/real-auth/status")
    suspend fun getRealAuthStatus(): ApiResponse<RealAuthStatus>

    /** 提交实名认证 */
    @POST("api/user/real-auth")
    suspend fun submitRealAuth(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Void>

    /** 更新用户资料 */
    @PUT("api/user/profile")
    suspend fun updateProfile(@Body body: Map<String, @JvmSuppressWildcards String?>): ApiResponse<Void>

    /** 获取用户资料（返回 Map，含 nickname/avatarUrl/phone 等） */
    @GET("api/user/profile")
    suspend fun getUserProfile(): ApiResponse<Map<String, @JvmSuppressWildcards Any>>

    /** 获取邀请链接 */
    @GET("api/user/invite/link")
    suspend fun getInviteLink(): ApiResponse<Map<String, String>>

    /** 修改密码 */
    @PUT("api/user/password")
    suspend fun changePassword(@Body body: Map<String, @JvmSuppressWildcards String>): ApiResponse<Void>

    /** 申请提现 */
    @POST("api/user/withdraw/apply")
    suspend fun applyWithdraw(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<Void>

    /** 提现记录 */
    @GET("api/user/withdraw/records")
    suspend fun getWithdrawRecords(): ApiResponse<List<WithdrawRecord>>

    /** 绑定钱包 */
    @POST("api/user/wallet/bind")
    suspend fun bindWallet(@Body body: Map<String, @JvmSuppressWildcards String>): ApiResponse<Void>

    /** 解绑钱包 */
    @DELETE("api/user/wallet/{type}")
    suspend fun unbindWallet(@Path("type") type: Int): ApiResponse<Void>

    // ==================== 任务模块 ====================

    /** 任务列表（分页+筛选） */
    @GET("api/task/tasks")
    suspend fun getTasks(
        @Query("platform") platform: Int?,
        @Query("type") type: Int?,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<PageResponse<TaskDTO>>

    /** 任务详情 */
    @GET("api/task/tasks/{id}")
    suspend fun getTaskDetail(@Path("id") id: Long): ApiResponse<TaskDTO>

    /** 接受任务 */
    @POST("api/task/tasks/{id}/accept")
    suspend fun acceptTask(@Path("id") id: Long): ApiResponse<Void>

    /** 放弃任务 */
    @POST("api/task/tasks/{id}/abandon")
    suspend fun abandonTask(@Path("id") id: Long): ApiResponse<Void>

    /** 我的任务记录 */
    @GET("api/task/tasks/records")
    suspend fun getMyTasks(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<PageResponse<TaskDTO>>

    /** 提交任务截图 */
    @POST("api/task/tasks/{id}/submit")
    suspend fun submitTask(
        @Path("id") id: Long,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): ApiResponse<Void>

    /** 获取当前用户对指定任务的记录 */
    @GET("api/task/tasks/{id}/record")
    suspend fun getTaskRecord(@Path("id") id: Long): ApiResponse<TaskRecordDTO>

    /** 获取任务记录详情 */
    @GET("api/task/tasks/records/{recordId}")
    suspend fun getTaskRecordDetail(@Path("recordId") recordId: Long): ApiResponse<TaskRecordDTO>

    // ==================== 收益模块 ====================

    /** 收益概览 */
    @GET("api/user/earnings/summary")
    suspend fun getEarningsSummary(): ApiResponse<EarningsSummary>

    /** 收益明细记录 */
    @GET("api/user/earnings/records")
    suspend fun getEarningsRecords(
        @Query("type") type: Int? = null,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<PageResponse<EarningsRecord>>

    // ==================== 文件上传 ====================

    /** 上传收款码（迁移至 task-upload-service） */
    @Multipart
    @POST("api/upload/wallet-qrcode")
    suspend fun uploadWalletQrcode(
        @Part file: MultipartBody.Part
    ): ApiResponse<UploadResult>

    /** 上传单张图片（迁移至 task-upload-service） */
    @Multipart
    @POST("api/upload/image")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
        @Part("type") type: okhttp3.RequestBody
    ): ApiResponse<UploadResult>

    // ==================== 自动化操作记录模块 ====================

    /** 保存自动化操作日志 */
    @POST("api/task/auto/record")
    suspend fun saveAutoRecord(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResponse<AutoRecord>

    /** 查询某任务的自动化操作日志 */
    @GET("api/task/auto/records")
    suspend fun getAutoRecords(@Query("taskId") taskId: Long): ApiResponse<List<AutoRecord>>

    /** 获取评论词（按分类ID） */
    @GET("api/task/auto/comment-words")
    suspend fun getCommentWords(@Query("categoryIds") categoryIds: String): ApiResponse<List<String>>

    // ==================== 发布任务模块 ====================

    /** 获取发布任务列表 */
    @GET("api/mobile/publish/tasks")
    suspend fun getPublishTasks(): ApiResponse<List<PublishTaskDTO>>

    /** 领取发布任务 */
    @POST("api/mobile/publish/tasks/{id}/claim")
    suspend fun claimPublishTask(@Path("id") id: Long): ApiResponse<Void>

    /** 获取我的发布任务列表 */
    @GET("api/mobile/publish/tasks/my")
    suspend fun getMyPublishTasks(): ApiResponse<List<PublishTaskDTO>>

    /** 完成发布任务 */
    @POST("api/mobile/publish/tasks/{id}/complete")
    suspend fun completePublishTask(@Path("id") id: Long): ApiResponse<Void>
}

/**
 * API客户端 - 单例模式
 * 
 * 使用方式：
 *   ApiClient.apiService.login(...)   // 通过 Hilt 注入的 Repository 中使用
 *   ApiClient.setToken(token)         // 登录成功后设置Token
 */
object ApiClient {
    private const val BASE_URL = BuildConfig.BASE_URL

    private var _instance: ApiService? = null
    @Volatile
    var token: String = ""
        private set

    /** 便捷属性：直接访问 ApiService（无 Context 依赖的场景） */
    val apiService: ApiService
        get() {
            if (_instance == null) {
                _instance = createApiService()
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

    private fun createApiService(): ApiService {
        // 添加日志拦截器
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            android.util.Log.d("ApiClient", "OkHttp: $message")
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY // 打印完整请求和响应
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor) // 添加日志拦截器
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                if (token.isNotEmpty()) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
                android.util.Log.d("ApiClient", "===== 发送请求 =====")
                android.util.Log.d("ApiClient", "URL: ${original.url}")
                android.util.Log.d("ApiClient", "Method: ${original.method}")
                builder.build().let { request ->
                    android.util.Log.d("ApiClient", "Headers: ${request.headers}")
                    chain.proceed(request)
                }
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

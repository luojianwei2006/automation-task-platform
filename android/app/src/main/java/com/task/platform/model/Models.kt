package com.task.platform.model

import com.google.gson.annotations.SerializedName

/**
 * 统一API响应体（与后端ApiResponse对应）
 */
data class ApiResponse<T>(
    val code: Int = 0,
    val msg: String? = null,
    val data: T? = null,
    val timestamp: Long = 0
)

/**
 * 登录/注册响应数据
 */
data class LoginResponse(
    @SerializedName("token") val token: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("expiresIn") val expiresIn: Long,
    @SerializedName("userInfo") val userInfo: UserInfo
)

/**
 * 用户信息
 */
data class UserInfo(
    val id: Long,
    val phone: String,
    val nickname: String?,
    val avatarUrl: String?,
    @SerializedName("realAuthStatus") val realAuthStatus: Int,
    @SerializedName("inviteCode") val inviteCode: String?
)

/**
 * 任务DTO
 */
data class TaskDTO(
    val id: Long,
    val title: String,
    /** 1抖音 2小红书 */
    val platform: Int,
    /** 1点赞 2评论 */
    val taskType: Int,
    @SerializedName("targetUrl") val targetUrl: String,
    @SerializedName("rewardAmount") val rewardAmount: Double,
    @SerializedName("totalQuota") val totalQuota: Int,
    @SerializedName("usedQuota") val usedQuota: Int,
    val status: Int
)

/**
 * 分页响应
 */
data class PageResponse<T>(
    val records: List<T>,
    val total: Long,
    val current: Long,
    val size: Long
)

/**
 * 收益概览
 */
data class EarningsSummary(
    @SerializedName("totalEarnings") val totalEarnings: Double,
    @SerializedName("availableBalance") val availableBalance: Double,
    @SerializedName("todayEarnings") val todayEarnings: Double
)

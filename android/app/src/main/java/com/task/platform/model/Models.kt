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
    val autoMode: Int = 0,
    @SerializedName("inviteCode") val inviteCode: String?,
    @SerializedName("wechatAccount") val wechatAccount: String? = null,
    @SerializedName("alipayAccount") val alipayAccount: String? = null,
    @SerializedName("wechatQrcode") val wechatQrcode: String? = null,
    @SerializedName("alipayQrcode") val alipayQrcode: String? = null
)

/**
 * 任务DTO
 */
data class TaskDTO(
    val id: Long,
    val title: String?,
    /** 1抖音 2小红书 */
    val platform: Int,
    /** 1点赞 2评论 */
    val taskType: Int,
    @SerializedName("targetUrl") val targetUrl: String?,
    /** 任务要求（文本） */
    @SerializedName("requirements") val requirements: String?,
    /** 要求图片（JSON数组字符串，需解析） */
    @SerializedName("requirementImages") val requirementImages: String?,
    @SerializedName("rewardAmount") val rewardAmount: Double?,
    @SerializedName("totalQuota") val totalQuota: Int,
    @SerializedName("usedQuota") val usedQuota: Int,
    /** 每日限制 */
    @SerializedName("dailyLimit") val dailyLimit: Int?,
    /** 截止时间 */
    @SerializedName("deadline") val deadline: String?,
    val status: Int,
    /** 是否需要定位验证 */
    @SerializedName("requireLocation") val requireLocation: Boolean = false,
    /** 任务位置纬度 */
    @SerializedName("locationLat") val locationLat: Double? = null,
    /** 任务位置经度 */
    @SerializedName("locationLng") val locationLng: Double? = null,
    /** 位置描述 */
    @SerializedName("locationDesc") val locationDesc: String? = null
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

/**
 * 收益明细记录
 */
/**
 * 文件上传结果（与后端 UploadResult 对应）
 */
data class UploadResult(
    @SerializedName("relativePath") val relativePath: String,
    @SerializedName("accessUrl") val accessUrl: String,
    @SerializedName("filename") val filename: String,
    @SerializedName("size") val size: Long
)

data class EarningsRecord(
    val id: Long,
    /** 1任务收益 2邀请奖励 3提现 4其他 */
    val type: Int,
    val description: String,
    /** 正数=收入 负数=支出 */
    val amount: Double,
    val createdAt: String
)

/** 提现记录 */
data class WithdrawRecord(
    val id: Long,
    @SerializedName("withdrawNo") val withdrawNo: String,
    val amount: Double,
    val method: String,
    val account: String?,
    @SerializedName("realName") val realName: String?,
    val status: Int,
    @SerializedName("rejectReason") val rejectReason: String?,
    @SerializedName("transferVoucherUrl") val transferVoucherUrl: String?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("processedAt") val processedAt: String?
)

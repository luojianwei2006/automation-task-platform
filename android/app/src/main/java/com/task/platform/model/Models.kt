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
    /** 1抖音 2小红书 3微信视频号 */
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
    @SerializedName("locationDesc") val locationDesc: String? = null,
    /** 评论词分类ID（逗号分隔） */
    @SerializedName("commentCategoryIds") val commentCategoryIds: String? = null
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

/**
 * 自动化操作记录
 * 对应后端 t_auto_record 表
 */
data class AutoRecord(
    val id: Long = 0,
    val userId: Long,
    val taskId: Long,
    val step: String,
    val action: String,
    val status: Int = 0,
    val result: String? = null,
    val createdAt: String? = null
)

/**
 * 自动化任务配置（内部模型，非API实体）
 * 传递给 AutomationService/DouyinAutomator 的参数对象
 */
data class AutoTask(
    val platform: Int,
    val taskType: Int,
    val targetUrl: String?,
    val requirements: String?,
    val taskId: Long,
    val userId: Long,
    val commentCategoryIds: String? = null
)

// ==================== 发布任务模型 ====================

/**
 * 发布任务DTO
 */
data class PublishTaskDTO(
    val id: Long,
    val projectId: Long,
    val projectName: String = "",
    val platforms: String,
    val publishText: String? = null,
    val status: String,
    val scheduledAt: String? = null,
    val createdAt: String,
    /** 素材列表 */
    val materials: List<PublishMaterialDTO> = emptyList(),
    /** 提交记录状态：CLAIMED/MERGED/SUBMITTED/PASSED/REJECTED（列表精确态用） */
    val submissionStatus: String? = null
)

/**
 * 发布素材DTO
 */
data class PublishMaterialDTO(
    val id: Long,
    val type: String,
    val title: String? = null,
    val fileUrl: String? = null,
    val content: String? = null,
    val sortOrder: Int = 0
)

// ==================== 素材预览模型（移动端随机预览接口） ====================

/**
 * 素材列表项 VO（对应后端 MaterialListVO）
 */
data class MaterialListVO(
    val id: Long = 0,
    val projectId: Long = 0,
    val projectName: String? = null,
    val type: String = "",
    val title: String? = null,
    val fileUrl: String? = null,
    val fileSize: Long? = null,
    val content: String? = null,
    val duration: Int? = null,
    val resolution: String? = null,
    val sortOrder: Int = 0,
    val createdAt: String? = null,
)

/**
 * 视频分组 VO（按 sortOrder 分组，每组一个视频）
 */
data class VideoGroupVO(
    val sortOrder: Int = 0,
    val video: MaterialListVO? = null,
)

/**
 * 发布素材随机预览响应 VO
 * GET /api/mobile/publish/materials
 */
data class PublishMaterialPreviewVO(
    val textMaterial: MaterialListVO? = null,
    val imageMaterial: MaterialListVO? = null,
    val musicMaterial: MaterialListVO? = null,
    val videoGroups: List<VideoGroupVO> = emptyList(),
)

/**
 * 合并预览结果
 */
data class MergeResultVO(
    val url: String = "",
    val durationSeconds: Int? = null,
    val fileSize: Long? = null,
    val historyId: Long? = null
)

/** 合并历史记录 */
data class MergeHistoryVO(
    val id: Long = 0,
    val projectId: Long = 0,
    val videoIds: String? = null,
    val musicId: Long? = null,
    val outputUrl: String = "",
    val durationSeconds: Int? = null,
    val fileSize: Long? = null,
    val createdAt: String? = null
)

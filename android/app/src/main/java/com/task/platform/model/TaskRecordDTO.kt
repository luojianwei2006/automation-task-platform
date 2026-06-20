package com.task.platform.model

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime

/**
 * 用户任务记录 DTO
 * 对应后端 UserTaskRecord 实体
 *
 * 重要：所有字段必须标注 @SerializedName，防止 R8/ProGuard 混淆后
 * Gson 无法匹配 JSON 字段名导致反序列化为 null。
 */
data class TaskRecordDTO(
    @SerializedName("id") val id: Long,
    @SerializedName("userId") val userId: Long,
    @SerializedName("taskId") val taskId: Long,
    @SerializedName("screenshotUrl") val screenshotUrl: String?,
    @SerializedName("submitCount") val submitCount: Int,
    @SerializedName("status") val status: Int, // 0进行中 1待审核 2通过 3拒绝 4超时放弃
    @SerializedName("aiCheckResult") val aiCheckResult: String?,
    @SerializedName("reviewResult") val reviewResult: String?,
    @SerializedName("rewardAmount") val rewardAmount: Double?,
    @SerializedName("autoMode") val autoMode: Int?,
    @SerializedName("acceptedAt") val acceptedAt: String?, // ISO 8601 格式
    @SerializedName("acceptDeadline") val acceptDeadline: String?, // ISO 8601 格式
    @SerializedName("submittedAt") val submittedAt: String?, // ISO 8601 格式
    @SerializedName("aiCheckedAt") val aiCheckedAt: String?, // ISO 8601 格式
    @SerializedName("manualCheckedAt") val manualCheckedAt: String?, // ISO 8601 格式
    @SerializedName("rewardGrantedAt") val rewardGrantedAt: String?, // ISO 8601 格式
    @SerializedName("checkedAt") val checkedAt: String? // ISO 8601 格式
)

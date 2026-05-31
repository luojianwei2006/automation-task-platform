package com.task.platform.model

import java.time.LocalDateTime

/**
 * 用户任务记录 DTO
 * 对应后端 UserTaskRecord 实体
 */
data class TaskRecordDTO(
    val id: Long,
    val userId: Long,
    val taskId: Long,
    val screenshotUrl: String?,
    val submitCount: Int,
    val status: Int, // 0进行中 1待审核 2通过 3拒绝 4超时放弃
    val aiCheckResult: String?,
    val reviewResult: String?,
    val rewardAmount: Double?,
    val autoMode: Int?,
    val acceptedAt: String?, // ISO 8601 格式
    val acceptDeadline: String?, // ISO 8601 格式
    val submittedAt: String?, // ISO 8601 格式
    val aiCheckedAt: String?, // ISO 8601 格式
    val manualCheckedAt: String?, // ISO 8601 格式
    val rewardGrantedAt: String?, // ISO 8601 格式
    val checkedAt: String? // ISO 8601 格式
)

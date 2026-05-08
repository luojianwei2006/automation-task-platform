package com.task.platform.service

/**
 * 实名认证状态数据模型（与后端接口对齐）
 */
data class RealAuthStatus(
    val status: Int,         // 0未认证 1审核中 2已认证 3失败
    val statusDesc: String,
    val realName: String?,
    val idCardMasked: String?
)

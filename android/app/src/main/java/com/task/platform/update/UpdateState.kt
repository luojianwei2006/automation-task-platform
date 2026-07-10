package com.task.platform.update

/**
 * 版本更新 UI 状态机
 *
 * - [Idle]      无更新 / 未检查 / 已关闭（可选更新，用户选择「稍后再说」后回落到此）
 * - [Checking]  正在检查（一般极短，可不展示 UI）
 * - [Available] 检测到新版本，应弹出更新对话框
 * - [Downloading] 正在下载 APK
 * - [Error]     下载/安装出错（仅日志 + Toast 兜底，不阻塞用户）
 */
sealed class UpdateState {
    /** 无更新 / 未检查 / 已关闭 */
    object Idle : UpdateState()

    /** 检查中（极少展示） */
    object Checking : UpdateState()

    /** 有新版本（弹出更新对话框） */
    data class Available(
        val version: String,
        val url: String,
        val appName: String
    ) : UpdateState()

    /** 正在下载 */
    object Downloading : UpdateState()

    /** 下载/安装出错 */
    data class Error(val message: String) : UpdateState()
}

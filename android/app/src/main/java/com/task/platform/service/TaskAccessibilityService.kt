package com.task.platform.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 任务无障碍服务 - 半自动引导模式的核心组件
 * 
 * 功能：
 * 1. 检测用户是否点击了目标App的点赞/评论按钮
 * 2. 监听页面跳转，确认用户进入了目标任务页面
 * 3. 自动抓取截图（需用户授权）
 * 
 * 使用前提：
 * - 用户在设置中开启无障碍服务权限
 * - AndroidManifest.xml中正确注册本服务
 */
class TaskAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_LIKE_DETECTED = "com.task.platform.ACTION_LIKE_DETECTED"
        const val ACTION_COMMENT_DETECTED = "com.task.platform.ACTION_COMMENT_DETECTED"
        const val ACTION_PAGE_CHANGED = "com.task.platform.ACTION_PAGE_CHANGED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_TEXT_CONTENT = "text_content"

        var instance: TaskAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        // 配置无障碍服务：监听所有事件类型
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_GESTURE_DETECTION_START
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            packageNames = null // 监听所有App（生产环境可限制为目标App）
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            // 检测点击事件（点赞/评论按钮）
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleViewClicked(event)
            
            // 检测窗口变化（切换到目标App）
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowChanged(event)
            
            // 检测内容变化（评论输入框等）
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleContentChanged(event)
        }
    }

    /**
     * 处理点击事件 - 检测点赞/评论操作
     */
    private fun handleViewClicked(event: AccessibilityEvent) {
        val node = event.source ?: return
        val viewIdResourceName = node.viewIdResourceName ?: ""
        val contentDescription = node.contentDescription?.toString() ?: ""
        val textContent = node.text?.toString() ?: ""

        if (isLikeButton(node, viewIdResourceName, contentDescription)) {
            sendBroadcast(Intent(ACTION_LIKE_DETECTED).apply {
                putExtra(EXTRA_PACKAGE_NAME, event.packageName?.toString())
                putExtra(EXTRA_TEXT_CONTENT, textContent)
            })
        } else if (isCommentButton(viewIdResourceName, contentDescription)) {
            sendBroadcast(Intent(ACTION_COMMENT_DETECTED).apply {
                putExtra(EXTRA_PACKAGE_NAME, event.packageName?.toString())
                putExtra(EXTRA_TEXT_CONTENT, textContent)
            })
        }
    }

    /**
     * 处理窗口切换 - 检测进入目标App
     */
    private fun handleWindowChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        sendBroadcast(Intent(ACTION_PAGE_CHANGED).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
        })
    }

    /**
     * 处理内容变化 - 检测评论输入等
     */
    private fun handleContentChanged(event: AccessibilityEvent) {
        // 可扩展：检测评论框内容变化
    }

    /**
     * 判断是否为点赞按钮
     * 通过viewId、contentDescription、文本内容综合判断
     */
    private fun isLikeButton(
        node: AccessibilityNodeInfo,
        viewId: String,
        contentDesc: String
    ): Boolean {
        // 抖音点赞按钮特征
        val douyinLikeIds = listOf(
            "com.ss.android.ugc.aweme:id/awd",
            "com.bytedance.ies.xelement:id/like_icon",
        )
        // 小红书点赞按钮特征
        val xiaohongshuLikeIds = listOf(
            "com.xingin.xhs:id/like",
        )

        val allLikeIds = douyinLikeIds + xiaohongshuLikeIds
        return viewId in allLikeIds ||
                contentDesc.contains("点赞", ignoreCase = true) ||
                contentDesc.contains("like", ignoreCase = true)
    }

    /**
     * 判断是否为评论按钮/输入框
     */
    private fun isCommentButton(viewId: String, contentDesc: String): Boolean {
        return viewId.contains("comment") ||
                contentDesc.contains("评论", ignoreCase = true) ||
                contentDesc.contains("comment", ignoreCase = true)
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}

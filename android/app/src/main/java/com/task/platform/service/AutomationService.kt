package com.task.platform.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.task.platform.model.AutoTask

/**
 * 自动化无障碍服务 — 深度自动化模式核心
 *
 * 功能：
 * - 接收 AutoTask 任务配置，按序执行平台自动化操作
 * - 每一步通过 AccessibilityNodeInfo 查找目标元素并模拟点击/输入
 * - 每步操作异步上报后端日志
 * - 模拟真人行为（随机延迟、随机滚动）
 *
 * 使用前提：
 * - 用户在系统设置中手动开启本无障碍服务
 * - AndroidManifest.xml 中已注册本服务
 */
class AutomationService : AccessibilityService() {

    companion object {
        /** 全局单例引用，供 DouyinAutomator 获取 rootInActiveWindow */
        var instance: AutomationService? = null
            private set

        /** 自动化操作结果回调 */
        var onActionResult: ((Boolean, String) -> Unit)? = null

        /** 是否为正在运行中 */
        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * 检查无障碍服务是否已开启
         */
        fun isAccessibilityEnabled(context: Context): Boolean {
            val pkg = context.packageName
            // 系统既可能存缩写 com.task.platform/.service.AutomationService
            // 也可能存完整 com.task.platform/com.task.platform.service.AutomationService
            val shortName = "$pkg/.service.AutomationService"
            val fullName = "$pkg/$pkg.service.AutomationService"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabledServices.split(':').any {
                it.equals(shortName, ignoreCase = true) || it.equals(fullName, ignoreCase = true)
            }
        }

        /**
         * 打开无障碍服务设置页面
         */
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        /**
         * 标记运行状态
         */
        fun setRunning(running: Boolean) {
            isRunning = running
        }
    }

    /** 当前正在执行的任务配置 */
    private var currentTask: AutoTask? = null

    /** 自动化引擎 */
    private val douyinAutomator: DouyinAutomator by lazy { DouyinAutomator(this) }
    private val xhsAutomator: XhsAutomator by lazy { XhsAutomator(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 自动化服务的事件处理由 DouyinAutomator 内部自行查找节点
        // 不在此处消费事件
    }

    override fun onInterrupt() {
        isRunning = false
        onActionResult?.invoke(false, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        onActionResult = null
    }

    /**
     * 执行自动化任务
     * 由 AutoViewModel 调用，传入 AutoTask 配置
     */
    fun execute(task: AutoTask) {
        if (isRunning) {
            onActionResult?.invoke(false, "已有任务正在执行中")
            return
        }
        currentTask = task
        isRunning = true

        Thread {
            try {
                when (task.platform) {
                    1 -> douyinAutomator.execute(task)
                    2 -> xhsAutomator.execute(task)
                    else -> {
                        onActionResult?.invoke(false, "暂不支持该平台: ${task.platform}")
                        isRunning = false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AutomationService", "自动化执行异常", e)
                onActionResult?.invoke(false, "自动化执行异常: ${e.message}")
                isRunning = false
            }
        }.start()
    }

    /**
     * 停止当前自动化任务
     */
    fun stop() {
        isRunning = false
        douyinAutomator.cancel()
        onActionResult?.invoke(false, "用户手动停止")
    }
}

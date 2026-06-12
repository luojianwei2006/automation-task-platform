package com.task.platform.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * 自动化浮动窗口 — 在所有应用之上显示当前自动化状态
 * 通过 AutomationService 的 companion object 推送步骤更新
 */
class AutomationOverlayService : Service() {

    companion object {
        @Volatile var instance: AutomationOverlayService? = null
        private const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "automation_overlay"

        // 重试机制：最多 100 次 × 50ms = 5 秒等待窗口（覆盖 Service 重启/冷启动）
        private const val MAX_RETRY_COUNT = 100
        private const val RETRY_DELAY_MS = 50L

        /** 待处理更新队列：当 instance==null 且重试耗尽时，存入此队列，Service 就绪后回放 */
        private val pendingUpdates = mutableListOf<Runnable>()
        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        /**
         * 代数计数器：每次 show() +1，防止旧任务 updateComplete 的 3s hide 定时器
         * 误杀新任务的悬浮窗。updateComplete 只会在代数匹配时执行 hide。
         */
        @Volatile private var generation = 0

        fun show(context: Context, taskName: String) {
            android.util.Log.d("AutomationOverlay", "show() 调用: taskName=$taskName, sdk=${Build.VERSION.SDK_INT}")

            // 检查悬浮窗权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !android.provider.Settings.canDrawOverlays(context)) {
                android.util.Log.e("AutomationOverlay", "show() 被跳过：悬浮窗权限(SYSTEM_ALERT_WINDOW)未开启！请在 设置→应用→权限 中开启")
                return
            }

            // 检查通知权限 (Android 13+, targetSdk >= 33)
            if (Build.VERSION.SDK_INT >= 33) {
                val notifGranted = if (Build.VERSION.SDK_INT >= 33) {
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                } else true
                android.util.Log.d("AutomationOverlay", "show() POST_NOTIFICATIONS 权限: ${if (notifGranted) "已授权" else "未授权！startForeground 会崩溃"}")
                if (!notifGranted) {
                    android.util.Log.e("AutomationOverlay", 
                        "show() POST_NOTIFICATIONS 未授权！Service startForeground() 将抛出 SecurityException。" +
                        "请在 设置→应用→通知 中开启通知权限，或等待 APK 下次启动时的权限弹窗。")
                }
            }

            generation++
            android.util.Log.d("AutomationOverlay", "show() generation=$generation, 准备启动 Service")
            val intent = Intent(context, AutomationOverlayService::class.java).apply {
                putExtra("taskName", taskName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, AutomationOverlayService::class.java))
        }

        /** 检查 Service 是否存活（避免竞态：instance 非 null 不代表 View 已创建） */
        fun isActive(): Boolean = instance != null && instance?.overlayView != null

        fun updateStep(step: String, status: String, retryCount: Int = 0) {
            val inst = instance
            if (inst != null && inst.overlayView != null) {
                inst.handler.post {
                    inst.stepText?.text = step
                    inst.statusText?.text = status
                }
            } else if (retryCount < MAX_RETRY_COUNT) {
                mainHandler.postDelayed({
                    updateStep(step, status, retryCount + 1)
                }, RETRY_DELAY_MS)
            } else {
                android.util.Log.e("AutomationOverlay", "updateStep 重试耗尽(${MAX_RETRY_COUNT}次): step=$step, instance=$instance, overlayView=${instance?.overlayView}")
                // 保存到待处理队列，Service 就绪后回放
                pendingUpdates.add(Runnable {
                    instance?.let {
                        it.stepText?.text = step
                        it.statusText?.text = status
                    }
                })
            }
        }

        fun updateComplete(success: Boolean, retryCount: Int = 0) {
            val inst = instance
            val gen = generation  // 捕获当前代数，防止旧定时器误杀新任务
            if (inst != null && inst.overlayView != null) {
                inst.handler.post {
                    val text = if (success) "✓ 任务完成" else "✗ 任务失败"
                    inst.stepText?.text = text
                    inst.statusText?.visibility = View.GONE
                    inst.handler.postDelayed({
                        // 仅当代数匹配且 instance 仍是同一个时才 hide（防止误杀新任务）
                        if (generation == gen && instance == inst) hide(inst)
                    }, 3000)
                }
            } else if (retryCount < MAX_RETRY_COUNT) {
                mainHandler.postDelayed({
                    updateComplete(success, retryCount + 1)
                }, RETRY_DELAY_MS)
            } else {
                android.util.Log.e("AutomationOverlay", "updateComplete 重试耗尽(${MAX_RETRY_COUNT}次): success=$success, instance=$instance, overlayView=${instance?.overlayView}")
                // 保存到待处理队列（含 3 秒后自动隐藏，同样用代数保护）
                pendingUpdates.add(Runnable {
                    instance?.let { inst2 ->
                        val gen2 = generation
                        val text = if (success) "✓ 任务完成" else "✗ 任务失败"
                        inst2.stepText?.text = text
                        inst2.statusText?.visibility = View.GONE
                        inst2.handler.postDelayed({
                            if (generation == gen2 && instance == inst2) hide(inst2)
                        }, 3000)
                    }
                })
            }
        }

        /** Service 就绪后回放所有待处理更新 */
        private fun flushPending() {
            if (pendingUpdates.isEmpty()) return
            android.util.Log.d("AutomationOverlay", "回放 ${pendingUpdates.size} 条待处理更新")
            val inst = instance ?: return
            for (update in pendingUpdates) {
                inst.handler.post(update)
            }
            pendingUpdates.clear()
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var stepText: TextView? = null
    private var statusText: TextView? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        android.util.Log.d("AutomationOverlay", "Service onCreate: instance=$this")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskName = intent?.getStringExtra("taskName") ?: "自动化任务"
        try {
            startForeground(NOTIFICATION_ID, buildNotification(taskName))
            android.util.Log.d("AutomationOverlay", "Service onStartCommand: startForeground 成功")
        } catch (e: SecurityException) {
            android.util.Log.e("AutomationOverlay",
                "startForeground 失败（缺少 POST_NOTIFICATIONS 权限），但仍继续创建悬浮窗。" +
                "注意：无前台通知，系统可能在 5 秒后杀掉 Service。", e)
        } catch (e: Exception) {
            android.util.Log.e("AutomationOverlay", "startForeground 未知异常", e)
        }
        createOverlay(taskName)
        flushPending()
        android.util.Log.d("AutomationOverlay", "Service onStartCommand: overlay ready, flushed pending")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        android.util.Log.d("AutomationOverlay", "Service onDestroy: clearing instance, pending=${pendingUpdates.size}")
        super.onDestroy()
        instance = null
        removeOverlay()
    }

    private fun createOverlay(taskName: String) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 18)
            setBackgroundColor(0xDD000000.toInt())
            // 圆角效果
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 24f)
                }
            }
            // 标题
            addView(TextView(context).apply {
                text = "🤖 $taskName"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            // 步骤
            stepText = TextView(context).apply {
                text = "准备中..."
                setTextColor(0xFFFFB347.toInt())
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 10, 0, 0)
            }
            addView(stepText!!)
            // 状态
            statusText = TextView(context).apply {
                text = ""
                setTextColor(0xCCFFFFFF.toInt())
                textSize = 12f
                setPadding(0, 4, 0, 0)
            }
            addView(statusText!!)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 80

        windowManager?.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "自动化状态", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "自动化任务执行中" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(taskName: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("自动化任务执行中")
            .setContentText(taskName)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}

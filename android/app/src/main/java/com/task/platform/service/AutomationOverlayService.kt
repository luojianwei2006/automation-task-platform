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

        fun show(context: Context, taskName: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !android.provider.Settings.canDrawOverlays(context)) {
                android.util.Log.w("AutomationOverlay", "悬浮窗权限未开启，跳过")
                return
            }
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

        fun updateStep(step: String, status: String) {
            instance?.apply {
                handler.post {
                    stepText?.text = step
                    statusText?.text = status
                }
            }
        }

        fun updateComplete(success: Boolean) {
            instance?.apply {
                handler.post {
                    val text = if (success) "✓ 任务完成" else "✗ 任务失败"
                    stepText?.text = text
                    statusText?.visibility = View.GONE
                    handler.postDelayed({ instance?.let { hide(it) } }, 3000)
                }
            }
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskName = intent?.getStringExtra("taskName") ?: "自动化任务"
        startForeground(NOTIFICATION_ID, buildNotification(taskName))
        createOverlay(taskName)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
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

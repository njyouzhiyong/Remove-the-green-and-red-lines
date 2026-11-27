package com.linehider

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

/**
 * 全局遮罩覆盖层前台服务。
 *
 * 通过 WindowManager 添加一个全屏、不可触摸、不可聚焦的覆盖层，
 * 用 [LineMaskView] 在指定位置绘制黑色竖条，遮住屏幕上的坏线。
 *
 * 监听 SharedPreferences 变化，设置改动后实时刷新覆盖层。
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var maskView: LineMaskView
    private lateinit var appPrefs: AppPreferences

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshOverlay()
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        appPrefs = AppPreferences(this)
        maskView = LineMaskView(this)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(maskView, params)
        appPrefs.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        refreshOverlay()

        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 服务被系统杀死后不自动重建（由用户/开机广播控制启停）
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        appPrefs.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        if (::maskView.isInitialized) {
            try {
                windowManager.removeView(maskView)
            } catch (_: Exception) {
                // View 已被移除，忽略
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 重新读取配置并刷新绘制。 */
    private fun refreshOverlay() {
        if (!appPrefs.overlayEnabled) {
            maskView.updateLines(emptyList())
            return
        }
        maskView.updateLines(appPrefs.getLines())
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "屏幕遮罩",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "去红线绿线覆盖层常驻通知"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("去红线绿线")
            .setContentText("屏幕遮罩运行中")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}

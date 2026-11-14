package com.dynamicisland.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast

/**
 * 前台服务：把 [IslandView] 以系统悬浮窗的方式挂到屏幕顶部。
 */
class IslandService : Service(), IslandView.Callback {

    companion object {
        const val ACTION_SHOW = "com.dynamicisland.app.action.SHOW"
        const val ACTION_HIDE = "com.dynamicisland.app.action.HIDE"
        private const val CHANNEL_ID = "island_foreground"
        private const val NOTIFICATION_ID = 1001
        private const val AUTO_COLLAPSE_DELAY = 4500L

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var islandView: IslandView? = null
    private var attached = false
    private var offsetX = 0
    private var offsetY = 0

    private val handler = Handler(Looper.getMainLooper())

    private val collapseRunnable = Runnable { islandView?.setExpanded(false) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true
        IslandBus.onContent = { content -> showContent(content) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            removeIsland()
            stopSelf()
            return START_NOT_STICKY
        }
        attachIsland()
        return START_STICKY
    }

    private fun attachIsland() {
        if (attached) return
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予「显示在其他应用上层」权限", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        val view = IslandView(this)
        view.callback = this
        view.applyContent(
            IslandContent(
                appName = "灵动岛",
                title = "已就绪",
                text = "新通知会在这里展开显示",
                accentColor = 0xFF34C759.toInt()
            )
        )

        params = WindowManager.LayoutParams(
            view.collapsedWidth.toInt(),
            view.collapsedHeight.toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(10)
        }

        runCatching {
            windowManager.addView(view, params)
            islandView = view
            attached = true
        }.onFailure {
            Toast.makeText(this, "悬浮窗创建失败：${it.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun showContent(content: IslandContent) {
        val view = islandView ?: return
        view.applyContent(content)
        view.setExpanded(true)
        handler.removeCallbacks(collapseRunnable)
        handler.postDelayed(collapseRunnable, AUTO_COLLAPSE_DELAY)
    }

    // ---- IslandView.Callback ----

    override fun onSizeChanged(widthPx: Int, heightPx: Int) {
        val view = islandView ?: return
        if (!attached) return
        params.width = widthPx
        params.height = heightPx
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    override fun onTap() {
        val view = islandView ?: return
        val expand = !view.isExpanded()
        view.setExpanded(expand)
        handler.removeCallbacks(collapseRunnable)
        if (expand) handler.postDelayed(collapseRunnable, AUTO_COLLAPSE_DELAY)
    }

    override fun onDrag(dx: Int, dy: Int) {
        val view = islandView ?: return
        if (!attached) return
        offsetX += dx
        offsetY += dy
        params.x = offsetX
        params.y = dp(10) + offsetY
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    override fun onDismiss() {
        removeIsland()
        stopSelf()
    }

    // ---- 生命周期 ----

    private fun removeIsland() {
        handler.removeCallbacks(collapseRunnable)
        islandView?.let { view -> runCatching { windowManager.removeView(view) } }
        islandView = null
        attached = false
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "灵动岛运行状态",
            NotificationManager.IMPORTANCE_MIN
        ).apply { setShowBadge(false) }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("灵动岛运行中")
            .setContentText("通知会以悬浮胶囊的形式显示")
            .setSmallIcon(R.drawable.ic_stat_island)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        IslandBus.onContent = null
        removeIsland()
        super.onDestroy()
    }
}

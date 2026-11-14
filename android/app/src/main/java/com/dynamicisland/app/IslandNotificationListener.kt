package com.dynamicisland.app

import android.app.Notification
import android.graphics.Color
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlin.math.abs

/**
 * 监听系统通知，把内容推给灵动岛。
 * 需要在「设置 → 通知 → 通知使用权」里手动勾选本应用。
 */
class IslandNotificationListener : NotificationListenerService() {

    private val accentByPackage = mapOf(
        "com.tencent.mm" to 0xFF34C759.toInt(),
        "com.tencent.mobileqq" to 0xFF12B7F5.toInt(),
        "com.tencent.tim" to 0xFF12B7F5.toInt(),
        "com.tencent.wework" to 0xFF2F7DFF.toInt(),
        "com.ss.android.ugc.aweme" to 0xFFFF2C55.toInt(),
        "com.xingin.xhs" to 0xFFFF2442.toInt(),
        "com.tencent.music.lite" to 0xFFFF5A5F.toInt(),
        "com.netease.cloudmusic" to 0xFFE60026.toInt()
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        if (statusBarNotification.packageName == packageName) return

        val notification = statusBarNotification.notification ?: return
        // 跳过通知组摘要，避免同一条消息显示两遍
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        // 跳过没有内容（只有进度条之类）的通知
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (
            extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
            )?.toString().orEmpty()

        if (title.isBlank() && text.isBlank()) return

        val appName = appLabel(statusBarNotification.packageName)
        IslandBus.push(
            IslandContent(
                appName = appName,
                title = title.ifBlank { appName },
                text = text.ifBlank { "新消息" },
                accentColor = accentColor(statusBarNotification.packageName, notification.color)
            )
        )
    }

    private fun appLabel(pkg: String): String = runCatching {
        val pm = packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    }.getOrDefault(pkg)

    private fun accentColor(pkg: String, notificationColor: Int): Int {
        accentByPackage[pkg]?.let { return it }

        // 通知自带强调色时优先使用（太暗或透明的不要）
        val r = Color.red(notificationColor)
        val g = Color.green(notificationColor)
        val b = Color.blue(notificationColor)
        if (Color.alpha(notificationColor) > 200 && r + g + b > 120) return notificationColor

        // 兜底：按包名散列出一个稳定色相
        val hue = (abs(pkg.hashCode()) % 360).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.95f))
    }
}

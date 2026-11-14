package com.dynamicisland.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusOverlay: TextView
    private lateinit var statusNotification: TextView
    private lateinit var previewIsland: IslandView
    private lateinit var toggleButton: Button

    private var previewExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusOverlay = findViewById(R.id.status_overlay)
        statusNotification = findViewById(R.id.status_notification)
        previewIsland = findViewById(R.id.preview_island)
        toggleButton = findViewById(R.id.btn_toggle)

        // 页面内的实时预览，点一下就能看展开动画
        previewIsland.applyContent(
            IslandContent(
                appName = "微信",
                title = "丁源",
                text = "点我看看展开效果 →",
                accentColor = 0xFF34C759.toInt()
            )
        )
        previewIsland.setOnClickListener {
            previewExpanded = !previewExpanded
            previewIsland.setExpanded(previewExpanded)
        }

        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        findViewById<Button>(R.id.btn_notification_access).setOnClickListener {
            runCatching {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }.onFailure {
                Toast.makeText(this, "打不开通知使用权页面，请到系统设置里手动开启", Toast.LENGTH_LONG).show()
            }
        }

        toggleButton.setOnClickListener { toggleService() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun toggleService() {
        if (IslandService.isRunning) {
            startService(Intent(this, IslandService::class.java).setAction(IslandService.ACTION_HIDE))
        } else {
            if (!Settings.canDrawOverlays(this)) {
                toast("先点「授予悬浮窗权限」，不然胶囊显示不出来")
                return
            }
            val intent = Intent(this, IslandService::class.java).setAction(IslandService.ACTION_SHOW)
            startForegroundService(intent)
        }
        toggleButton.postDelayed({ refreshStatus() }, 400)
    }

    private fun refreshStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        statusOverlay.text = if (overlayOk) "✓ 悬浮窗权限：已授予" else "✗ 悬浮窗权限：未授予"

        val listenerOk = isNotificationAccessEnabled()
        statusNotification.text = if (listenerOk) "✓ 通知使用权：已开启" else "✗ 通知使用权：未开启"

        toggleButton.text = if (IslandService.isRunning) "关闭灵动岛" else "开启灵动岛"
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { it.contains(packageName) }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 7
    }
}

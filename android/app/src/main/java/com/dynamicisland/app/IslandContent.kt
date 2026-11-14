package com.dynamicisland.app

/**
 * 灵动岛上显示的一条内容。
 */
data class IslandContent(
    val appName: String,
    val title: String,
    val text: String,
    val accentColor: Int,
    val time: Long = System.currentTimeMillis()
)

/**
 * 通知监听服务 <-> 悬浮窗服务 之间的极简总线。
 * 两个 Service 同进程，直接用静态回调即可，不需要 IPC。
 */
object IslandBus {
    @Volatile
    var onContent: ((IslandContent) -> Unit)? = null

    fun push(content: IslandContent) {
        onContent?.invoke(content)
    }
}

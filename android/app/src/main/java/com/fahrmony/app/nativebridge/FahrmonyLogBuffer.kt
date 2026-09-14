package com.fahrmony.app.nativebridge

import java.util.concurrent.ConcurrentLinkedDeque

data class LogEntry(
    val id: String,
    val timestamp: Long,
    val type: String, // IM_NOTIFICATION, MEDIA_SESSION, SYSTEM, AUTO_PLAY
    val tag: String,
    val title: String,
    val content: String,
    val rawExtras: String? = null
)

object FahrmonyLogBuffer {
    private const val MAX_LOG_SIZE = 100
    private val buffer = ConcurrentLinkedDeque<LogEntry>()
    var onLogAdded: ((LogEntry) -> Unit)? = null

    // ponytail: 环形队列存储最新 100 条日志，超过自动出队，无需数据库复杂开销
    fun addLog(type: String, tag: String, title: String, content: String, rawExtras: String? = null) {
        val privateEvent = type.startsWith("IM_") || type.startsWith("CALL_")
        val entry = LogEntry(
            id = "${System.currentTimeMillis()}-${(1000..9999).random()}",
            timestamp = System.currentTimeMillis(),
            type = type,
            tag = tag,
            title = if (privateEvent) "通知桥接事件" else title.take(160),
            content = if (privateEvent) "内容不记录" else content.take(512),
            rawExtras = null
        )
        addEntry(entry)
        onLogAdded?.invoke(entry)
        // Deliberately omit payloads from system logs, including debug builds.
    }

    fun addEntry(entry: LogEntry) {
        buffer.addFirst(entry)
        while (buffer.size > MAX_LOG_SIZE) {
            buffer.pollLast()
        }
    }

    fun getLogs(): List<LogEntry> {
        return buffer.toList()
    }

    fun clear() {
        buffer.clear()
    }
}

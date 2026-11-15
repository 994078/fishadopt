package com.fishadopt.app.data

import com.fishadopt.app.util.SensitiveMasker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 日志仓库（PRD 4.6）。
 * 环形缓冲，单次会话最多保留 [MAX_LINES] 行，超出后丢弃最早内容。
 * 所有写入在进入缓冲前统一脱敏（PRD 4.5/8 章）。
 */
class LogRepository {

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun append(level: LogLevel, rawMessage: String) {
        val masked = SensitiveMasker.mask(rawMessage)
        val entry = LogEntry(timestamp = System.currentTimeMillis(), level = level, message = masked)
        val current = _entries.value
        val next = if (current.size >= MAX_LINES) {
            current.drop(1) + entry
        } else {
            current + entry
        }
        _entries.value = next
    }

    fun info(msg: String) = append(LogLevel.INFO, msg)
    fun warn(msg: String) = append(LogLevel.WARNING, msg)
    fun error(msg: String) = append(LogLevel.ERROR, msg)

    fun clear() {
        _entries.value = emptyList()
    }

    /** 导出为文本（已脱敏），用于“复制全部 / 导出文件”。 */
    fun exportText(): String = _entries.value.joinToString("\n") { it.asLine() }

    private companion object {
        const val MAX_LINES = 1000
    }
}

package com.fishadopt.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 单条日志记录。 */
data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String,
) {
    fun formattedTime(): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return fmt.format(Date(timestamp))
    }

    fun asLine(): String = "${formattedTime()} [${level.name}] $message"
}

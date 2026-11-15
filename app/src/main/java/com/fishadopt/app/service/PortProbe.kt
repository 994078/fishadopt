package com.fishadopt.app.service

import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 端口探测（PRD 3.4 / 4.3）。
 * 启动后轮询本机服务端口直至可用；超时阈值 60 秒，超时按启动失败处理。
 */
object PortProbe {

    const val DEFAULT_TIMEOUT_MS = 60_000L
    const val DEFAULT_INTERVAL_MS = 500L

    /** 判断端口是否可连接。 */
    fun isPortOpen(host: String, port: Int, connectTimeoutMs: Int = 1000): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            true
        }
    } catch (_: Exception) {
        false
    }

    /** 轮询等待端口就绪，超时返回 false。 */
    suspend fun waitForPort(
        host: String = "127.0.0.1",
        port: Int,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        intervalMs: Long = DEFAULT_INTERVAL_MS,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isPortOpen(host, port)) return true
            delay(intervalMs)
        }
        return isPortOpen(host, port)
    }
}

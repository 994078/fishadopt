package com.fishadopt.app.service

/**
 * 进程守护（PRD 4.3）。
 *
 * 监听进程退出事件：若在 [windowMs]（30 秒）内连续退出两次以上，则停止自动重启，
 * 避免无限重启造成发热与耗电。纯逻辑类，便于单元测试。
 */
class ProcessGuard(private val windowMs: Long = DEFAULT_WINDOW_MS) {

    private var consecutiveExits = 0
    private var lastExitTime = 0L

    /**
     * 进程退出时调用。
     * @param now 当前时间戳（毫秒），便于测试注入。
     * @return 是否应尝试自动重启（仅首次退出返回 true）。
     */
    fun onExit(now: Long): Boolean {
        if (now - lastExitTime > windowMs) consecutiveExits = 0
        consecutiveExits++
        lastExitTime = now
        return consecutiveExits < 2
    }

    /** 复位计数（成功启动后调用）。 */
    fun reset() {
        consecutiveExits = 0
        lastExitTime = 0L
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 30_000L
    }
}

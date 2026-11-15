package com.fishadopt.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessGuardTest {

    @Test
    fun restartsOnlyOnceWithinWindow() {
        val guard = ProcessGuard(windowMs = 30_000)
        assertTrue("首次退出应允许重启", guard.onExit(1000))
        assertFalse("30 秒内第二次退出应停止", guard.onExit(2000))
        assertFalse(guard.onExit(3000))
    }

    @Test
    fun resetsAfterWindow() {
        val guard = ProcessGuard(windowMs = 30_000)
        assertTrue(guard.onExit(1000))
        assertFalse(guard.onExit(2000))
        // 距上次 38 秒，超过 30 秒窗口，重新计数
        assertTrue(guard.onExit(40_000))
    }

    @Test
    fun resetClearsCount() {
        val guard = ProcessGuard(windowMs = 30_000)
        guard.onExit(1000)
        guard.reset()
        assertTrue(guard.onExit(2000))
    }
}

package com.fishadopt.app.data

import android.content.Context

/**
 * 可配置项存储（PRD 4.7）。
 * 集中所有开关，避免功能分散到多个入口。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("fish_adopt_settings", Context.MODE_PRIVATE)

    /** 崩溃自动重启（默认关闭，PRD 4.3）。 */
    var autoRestart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RESTART, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RESTART, value).apply()

    /** 后台保活开关。 */
    var backgroundKeepAlive: Boolean
        get() = prefs.getBoolean(KEY_KEEP_ALIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_ALIVE, value).apply()

    /** 通知开关。 */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    /** DSH 服务端口（真实 DSH web 默认 3080，实测确认；APP 通过 --port 覆盖传入）。 */
    var dshPort: Int
        get() = prefs.getInt(KEY_DSH_PORT, DEFAULT_DSH_PORT)
        set(value) = prefs.edit().putInt(KEY_DSH_PORT, value).apply()

    /** 是否已完成首次引导。 */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    private companion object {
        const val KEY_AUTO_RESTART = "auto_restart"
        const val KEY_KEEP_ALIVE = "keep_alive"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_DSH_PORT = "dsh_port"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val DEFAULT_DSH_PORT = 3080
    }
}

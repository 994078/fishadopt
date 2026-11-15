package com.fishadopt.app.di

import android.content.Context
import com.fishadopt.app.data.EnvironmentManager
import com.fishadopt.app.data.KeyStore
import com.fishadopt.app.data.LogRepository
import com.fishadopt.app.data.PermissionChecker
import com.fishadopt.app.data.SettingsRepository
import com.fishadopt.app.service.ServiceStateHolder

/**
 * 手动依赖容器（应用级单例）。
 * 不使用 Hilt/KSP，降低构建失败面；各依赖为进程内单例，供 Activity/Service/ViewModel 共用。
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val keyStore = KeyStore(context)
    val settings = SettingsRepository(context)
    val log = LogRepository()
    val environment = EnvironmentManager(context, log)
    val permissions = PermissionChecker(context)
    val serviceState = ServiceStateHolder()
}

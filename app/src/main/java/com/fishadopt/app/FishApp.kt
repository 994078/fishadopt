package com.fishadopt.app

import android.app.Application
import com.fishadopt.app.di.AppContainer

/** 应用入口，持有进程级依赖容器。 */
class FishApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

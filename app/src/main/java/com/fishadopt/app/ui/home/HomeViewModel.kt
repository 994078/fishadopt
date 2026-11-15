package com.fishadopt.app.ui.home

import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishadopt.app.data.EnvironmentState
import com.fishadopt.app.data.ServiceState
import com.fishadopt.app.di.AppContainer
import com.fishadopt.app.service.FishForegroundService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val environmentState: StateFlow<EnvironmentState> = container.environment.state
    val serviceState: StateFlow<ServiceState> = container.serviceState.state
    val progress: StateFlow<Float> = container.environment.progress
    val stage: StateFlow<String?> = container.serviceState.stage

    val installedVersion: String? get() = container.environment.installedVersion()

    init {
        viewModelScope.launch { container.environment.refreshState() }
    }

    fun prepareEnvironment() {
        viewModelScope.launch { container.environment.prepareEnvironment() }
    }

    fun startService() {
        val intent = FishForegroundService.startIntent(
            container.appContext, container.settings.dshPort
        )
        ContextCompat.startForegroundService(container.appContext, intent)
    }

    fun stopService() {
        val intent = FishForegroundService.stopIntent(container.appContext)
        container.appContext.startService(intent)
    }
}

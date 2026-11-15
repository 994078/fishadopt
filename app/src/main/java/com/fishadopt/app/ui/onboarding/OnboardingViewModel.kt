package com.fishadopt.app.ui.onboarding

import androidx.lifecycle.ViewModel
import com.fishadopt.app.data.PermissionChecker
import com.fishadopt.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _status = MutableStateFlow(container.permissions.current())
    val status: StateFlow<PermissionChecker.Status> = _status.asStateFlow()

    /** 每次进入页面实时刷新授权状态（PRD 4.1：不缓存结果）。 */
    fun refresh() {
        _status.value = container.permissions.current()
    }
}

package com.fishadopt.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.fishadopt.app.ui.chat.ChatViewModel
import com.fishadopt.app.ui.home.HomeViewModel
import com.fishadopt.app.ui.logs.LogsViewModel
import com.fishadopt.app.ui.onboarding.OnboardingViewModel
import com.fishadopt.app.ui.settings.SettingsViewModel

/** 手动 DI 的 ViewModel 工厂，映射各 ViewModel 与其依赖容器。 */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(container) as T
        modelClass.isAssignableFrom(OnboardingViewModel::class.java) -> OnboardingViewModel(container) as T
        modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(container) as T
        modelClass.isAssignableFrom(LogsViewModel::class.java) -> LogsViewModel(container) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(container) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}

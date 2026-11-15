package com.fishadopt.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishadopt.app.di.AppContainer
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings get() = container.settings
    val keyStore get() = container.keyStore

    fun maskedKey(): String? = keyStore.maskedKey()

    fun saveKey(key: String) = keyStore.saveKey(key)

    fun clearKey() = keyStore.clear()

    fun resetEnvironment() {
        viewModelScope.launch { container.environment.reset() }
    }
}

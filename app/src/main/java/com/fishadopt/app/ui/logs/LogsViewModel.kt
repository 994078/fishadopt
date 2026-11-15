package com.fishadopt.app.ui.logs

import androidx.lifecycle.ViewModel
import com.fishadopt.app.data.LogEntry
import com.fishadopt.app.di.AppContainer
import kotlinx.coroutines.flow.StateFlow

class LogsViewModel(private val container: AppContainer) : ViewModel() {

    val entries: StateFlow<List<LogEntry>> = container.log.entries

    fun clear() = container.log.clear()

    fun exportText(): String = container.log.exportText()
}

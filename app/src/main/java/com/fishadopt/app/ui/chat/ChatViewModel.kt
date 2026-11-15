package com.fishadopt.app.ui.chat

import androidx.lifecycle.ViewModel
import com.fishadopt.app.data.ServiceState
import com.fishadopt.app.di.AppContainer
import kotlinx.coroutines.flow.StateFlow

class ChatViewModel(private val container: AppContainer) : ViewModel() {

    val serviceState: StateFlow<ServiceState> = container.serviceState.state
    val port: StateFlow<Int> = container.serviceState.port

    /** dsh web 打印的带 token 访问地址。 */
    val webUrl: StateFlow<String?> = container.serviceState.webUrl

    /** 优先用带 token 的地址；token 尚未捕获时退回裸端口地址。 */
    val url: String get() = webUrl.value ?: "http://127.0.0.1:${port.value}"
}

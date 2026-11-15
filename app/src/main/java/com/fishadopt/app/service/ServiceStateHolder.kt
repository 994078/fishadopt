package com.fishadopt.app.service

import com.fishadopt.app.data.ServiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨前台服务与 UI 共享的运行状态（同进程单例）。
 * 服务负责写入，UI 通过 StateFlow 观察。
 */
class ServiceStateHolder {

    private val _state = MutableStateFlow(ServiceState.STOPPED)
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    private val _port = MutableStateFlow(DEFAULT_PORT)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _stage = MutableStateFlow<String?>(null)
    val stage: StateFlow<String?> = _stage.asStateFlow()

    /** dsh web 打印的带 token 访问地址（如 http://127.0.0.1:3080/?token=...）。 */
    private val _webUrl = MutableStateFlow<String?>(null)
    val webUrl: StateFlow<String?> = _webUrl.asStateFlow()

    fun setState(s: ServiceState) { _state.value = s }
    fun setPort(p: Int) { _port.value = p }
    fun setStage(s: String?) { _stage.value = s }
    fun setWebUrl(u: String?) { _webUrl.value = u }

    companion object {
        /** 与 data.SettingsRepository.DEFAULT_DSH_PORT 保持一致（真实 DSH web 默认 3080）。 */
        const val DEFAULT_PORT = 3080
    }
}

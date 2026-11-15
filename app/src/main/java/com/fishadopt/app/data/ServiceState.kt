package com.fishadopt.app.data

/**
 * 服务（容器内 DSH）运行状态。
 */
enum class ServiceState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
}

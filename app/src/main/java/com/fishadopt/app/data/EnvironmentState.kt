package com.fishadopt.app.data

/**
 * 环境状态（PRD 表5）。
 */
enum class EnvironmentState {
    /** 私有目录不存在 RootFS。 */
    UNINITIALIZED,

    /** 解压任务进行中。 */
    EXTRACTING,

    /** RootFS 存在且校验通过。 */
    READY,

    /** 校验失败或关键文件缺失。 */
    CORRUPTED,
}

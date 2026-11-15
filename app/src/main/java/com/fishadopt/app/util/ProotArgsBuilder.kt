package com.fishadopt.app.util

/**
 * 组装 proot-rs 启动参数（PRD 附录 A）。
 *
 * 参数含义需与所捆绑 proot-rs 版本的实际行为一致，如有差异以实测为准。
 * 其中 [Config.rootfsPath] 指向解压后的根文件系统目录，
 * `-b` 用于向容器暴露安卓系统目录，`--link2symlink` 用于规避安卓下符号链接创建失败的问题，
 * 是 DSH 文件操作正常工作的前提，不得省略。
 */
object ProotArgsBuilder {

    data class Config(
        /** proot-rs 二进制在应用私有目录中的绝对路径。 */
        val prootBinary: String,
        /** 解压后的 RootFS 绝对路径。 */
        val rootfsPath: String,
        /** 向容器暴露的宿主外部存储目录（映射到 /storage）。 */
        val externalStorageHost: String,
        /** 容器内挂载点，默认 /storage。 */
        val externalStorageGuest: String = "/storage",
        /** 容器内工作目录。 */
        val workDir: String = "/root",
        /** 容器内启动脚本路径。 */
        val startScript: String = "/root/start_dsh.sh",
    )

    fun build(cfg: Config): List<String> = buildList {
        add(cfg.prootBinary)
        add("--link2symlink") // 规避安卓符号链接创建失败，DSH 文件操作正常工作的前提
        add("-0")             // root-id：使部分系统调用按 root 语义执行
        add("-r"); add(cfg.rootfsPath)
        add("-b"); add("/dev:/dev")
        add("-b"); add("/proc:/proc")
        add("-b"); add("/sys:/sys")
        add("-b"); add("${cfg.externalStorageHost}:${cfg.externalStorageGuest}")
        add("-w"); add(cfg.workDir)
        add("/bin/bash"); add(cfg.startScript)
    }
}

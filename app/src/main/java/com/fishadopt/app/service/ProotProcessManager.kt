package com.fishadopt.app.service

import com.fishadopt.app.data.LogRepository
import com.fishadopt.app.util.ProotArgsBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * PRoot 进程托管（PRD 3.4 / 4.3）。
 *
 * 通过 [ProcessBuilder] 拉起 proot-rs，绑定系统目录，stdio 流式读入日志；
 * 优雅停止时先发终止信号、等待退出，超时后强制结束并记录。
 */
class ProotProcessManager(
    private val prootBinary: File,
    private val rootfsDir: File,
    private val externalStorageHost: String,
    private val port: Int,
    private val apiKey: String?,
    private val log: LogRepository,
    private val onWebUrl: ((String) -> Unit)? = null,
) {

    @Volatile
    private var process: Process? = null

    @Volatile
    private var stopRequested = false

    /** 启动 proot 进程。 */
    fun start(): Boolean {
        if (process?.isAlive == true) return false
        val args = ProotArgsBuilder.build(
            ProotArgsBuilder.Config(
                prootBinary = prootBinary.absolutePath,
                rootfsPath = rootfsDir.absolutePath,
                externalStorageHost = externalStorageHost,
            )
        )
        val pb = ProcessBuilder(args)
        pb.directory(rootfsDir.parentFile ?: rootfsDir)
        pb.redirectErrorStream(true)

        val env = pb.environment()
        // 密钥经环境变量注入（PRD 4.5：不落盘，进程结束即失效）
        apiKey?.let { env[ENV_DSH_KEY] = it }
        env[ENV_DSH_PORT] = port.toString()

        stopRequested = false
        val proc = pb.start()
        process = proc
        startStdoutReader(proc.inputStream)
        log.info("已启动 proot 进程，PID=${proc.pid()}")
        return true
    }

    fun isAlive(): Boolean = process?.isAlive == true

    /** 进程退出码（阻塞，应在 IO 调度器调用）。 */
    fun awaitExit(): Int = process?.waitFor() ?: -1

    /**
     * 优雅停止：先发终止信号并等待退出，超时后强制结束。
     * @return true 表示优雅退出，false 表示超时被强制结束。
     */
    suspend fun stopGracefully(timeoutMs: Long = 5000L): Boolean = withContext(Dispatchers.IO) {
        val proc = process ?: return@withContext true
        stopRequested = true
        if (!proc.isAlive) return@withContext true
        proc.destroy() // SIGTERM → 经 exec 链到达 node
        val exited = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!exited) {
            proc.destroyForcibly()
            log.warn("服务未在 ${timeoutMs}ms 内退出，已强制结束")
            false
        } else {
            true
        }
    }

    private fun startStdoutReader(input: InputStream) {
        thread(name = "proot-stdout", isDaemon = true) {
            input.bufferedReader().useLines { lines ->
                for (line in lines) {
                    // 捕获 dsh web 打印的带 token 访问地址，供 WebView 加载
                    val m = WEB_URL_REGEX.find(line)
                    if (m != null) onWebUrl?.invoke(m.groupValues[1])
                    log.info(line)
                }
            }
        }
    }

    companion object {
        const val ENV_DSH_KEY = "DSH_KEY"
        const val ENV_DSH_PORT = "DSH_PORT"

        /** 匹配 `dsh web: http://127.0.0.1:3080/?token=...` 行，捕获完整 URL。 */
        private val WEB_URL_REGEX = Regex("""dsh web:\s*(https?://\S+)""")
    }
}

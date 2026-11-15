package com.fishadopt.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fishadopt.app.FishApp
import com.fishadopt.app.MainActivity
import com.fishadopt.app.R
import com.fishadopt.app.data.EnvironmentState
import com.fishadopt.app.data.ServiceState
import com.fishadopt.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台服务（PRD 4.8）：托管容器内 DSH 的启停、常驻通知与进程守护。
 * 服务运行期间维持一条常驻通知（含“停止小肥鱼”“打开应用”两个快捷操作），
 * 停止后自动撤销。
 */
class FishForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var container: AppContainer
    private var processManager: ProotProcessManager? = null
    private val guard = ProcessGuard()

    override fun onCreate() {
        super.onCreate()
        container = (application as FishApp).container
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        val state = container.serviceState.state.value
        if (state == ServiceState.RUNNING || state == ServiceState.STARTING) return
        val port = intent.getIntExtra(EXTRA_PORT, container.settings.dshPort)
        startForegroundCompat()
        container.serviceState.setPort(port)
        scope.launch { runStartSequence(port) }
    }

    private suspend fun runStartSequence(port: Int) {
        val env = container.environment
        val holder = container.serviceState
        val log = container.log

        // 1. 校验运行前置条件（PRD 3.4）
        if (env.state.value != EnvironmentState.READY) {
            holder.setState(ServiceState.FAILED)
            log.error("环境未就绪，无法启动")
            updateNotification(getString(R.string.app_name), "环境未就绪")
            stopSelf()
            return
        }
        val apiKey = container.keyStore.getKey()
        if (apiKey.isNullOrBlank()) {
            holder.setState(ServiceState.FAILED)
            log.error("未填写 API Key，无法启动")
            updateNotification(getString(R.string.app_name), "未填写 API Key")
            stopSelf()
            return
        }

        holder.setState(ServiceState.STARTING)
        holder.setStage(getString(R.string.stage_starting_container))

        // 2. 构造并启动 proot 进程
        val externalHost = Environment.getExternalStorageDirectory().absolutePath
        val pm = ProotProcessManager(
            prootBinary = env.prootBinary,
            rootfsDir = env.rootfsDir,
            externalStorageHost = externalHost,
            port = port,
            apiKey = apiKey,
            log = log,
            onWebUrl = { holder.setWebUrl(it) },
        )
        processManager = pm
        if (!pm.start()) {
            holder.setState(ServiceState.FAILED)
            log.error("proot 进程启动失败")
            stopSelf()
            return
        }

        // 3. 轮询探测端口（PRD 3.4：60 秒超时按失败）
        holder.setStage(getString(R.string.stage_waiting_service))
        updateNotification(getString(R.string.app_name), getString(R.string.stage_waiting_service))
        val ready = PortProbe.waitForPort("127.0.0.1", port)
        if (!ready) {
            holder.setState(ServiceState.FAILED)
            holder.setStage(null)
            log.error("端口探测超时（${PortProbe.DEFAULT_TIMEOUT_MS / 1000}s），服务未就绪")
            updateNotification(getString(R.string.app_name), "服务启动超时")
            pm.stopGracefully()
            processManager = null
            stopSelf()
            return
        }

        // 4. 就绪
        guard.reset()
        holder.setState(ServiceState.RUNNING)
        holder.setStage(null)
        updateNotification(getString(R.string.app_name), "小肥鱼运行中")

        // 5. 监听进程退出（进程守护）
        val exitCode = pm.awaitExit()
        log.info("服务进程退出，exitCode=$exitCode")
        holder.setState(ServiceState.STOPPED)
        processManager = null

        if (guard.onExit(System.currentTimeMillis())) {
            log.warn("服务意外退出，尝试自动重启")
            runStartSequence(port)
        } else {
            log.error("服务反复崩溃，已停止自动重启，请查看日志（PRD 表9）")
            updateNotification(getString(R.string.app_name), "服务异常，请查看日志")
            stopSelf()
        }
    }

    private fun handleStop() {
        container.serviceState.setState(ServiceState.STOPPING)
        container.serviceState.setStage(null)
        scope.launch {
            val pm = processManager
            if (pm != null) {
                pm.stopGracefully()
                processManager = null
            }
            container.serviceState.setState(ServiceState.STOPPED)
            container.log.info("服务已停止")
            stopForegroundCompat()
            stopSelf()
        }
    }

    // ---- 通知 ----

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "小肥鱼运行状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "展示 DeepSeek Harness 运行状态，可快捷停止服务" }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FishForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fish)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, "停止小肥鱼", stopIntent)
            .addAction(0, "打开应用", openIntent)
            .build()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification(getString(R.string.app_name), "正在启动小肥鱼…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val ACTION_START = "com.fishadopt.app.action.START"
        const val ACTION_STOP = "com.fishadopt.app.action.STOP"
        const val EXTRA_PORT = "extra_port"
        const val CHANNEL_ID = "fish_running"
        const val NOTIFICATION_ID = 1001

        fun startIntent(context: Context, port: Int): Intent =
            Intent(context, FishForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, FishForegroundService::class.java).apply { action = ACTION_STOP }
    }
}

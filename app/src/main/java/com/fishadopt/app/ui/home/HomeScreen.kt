package com.fishadopt.app.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishadopt.app.data.EnvironmentState
import com.fishadopt.app.data.ServiceState
import com.fishadopt.app.di.AppContainer
import com.fishadopt.app.di.AppViewModelFactory

/** 主控制页（PRD 4.3：单主按钮，文案随状态变化）。 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenChat: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = AppViewModelFactory(container))
    val activity = LocalContext.current as? Activity

    val envState by vm.environmentState.collectAsState()
    val svcState by vm.serviceState.collectAsState()
    val progress by vm.progress.collectAsState()
    val stage by vm.stage.collectAsState()

    // 端口就绪后自动切入对话界面（PRD 3.4）
    LaunchedEffect(svcState) {
        if (svcState == ServiceState.RUNNING) onOpenChat()
    }

    // 运行中退出弹窗（PRD 4.3）
    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = svcState == ServiceState.RUNNING || svcState == ServiceState.STARTING) {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("小肥鱼仍在运行") },
            text = { Text("请选择退出方式：") },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) { Text("后台继续运行") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        vm.stopService()
                        activity?.finish()
                    }) { Text("停止并退出") }
                    TextButton(onClick = { showExitDialog = false }) { Text("取消") }
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("一键领养小肥鱼", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.weight(1f))

        // 主按钮
        val extracting = envState == EnvironmentState.EXTRACTING
        val starting = svcState == ServiceState.STARTING
        val running = svcState == ServiceState.RUNNING
        val corrupted = envState == EnvironmentState.CORRUPTED
        val uninit = envState == EnvironmentState.UNINITIALIZED

        val buttonText = when {
            extracting -> stage ?: "正在准备环境"
            starting -> stage ?: "正在启动"
            running -> "停止小肥鱼"
            corrupted || uninit -> "准备环境"
            else -> "一键领养小肥鱼"
        }
        val buttonEnabled = !extracting && !starting

        Button(
            onClick = {
                when {
                    running -> vm.stopService()
                    corrupted || uninit -> vm.prepareEnvironment()
                    else -> vm.startService()
                }
            },
            enabled = buttonEnabled,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(buttonText, style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(16.dp))

        // 状态行
        val statusText = when {
            extracting -> "正在准备环境 ${(progress * 100).toInt()}%"
            starting -> stage ?: "正在启动"
            running -> "小肥鱼运行中"
            corrupted -> "环境异常，请重新准备"
            uninit -> "环境未准备"
            else -> "就绪（DSH ${vm.installedVersion ?: "未知版本"}）"
        }
        Text(statusText, style = MaterialTheme.typography.bodyMedium)

        // 解压进度条
        if (extracting) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.weight(1f))

        // 快捷入口
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            OutlinedButton(onClick = onOpenLogs) { Text("日志") }
            OutlinedButton(onClick = onOpenSettings) { Text("设置") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

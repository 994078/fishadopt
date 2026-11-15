package com.fishadopt.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishadopt.app.di.AppContainer
import com.fishadopt.app.di.AppViewModelFactory

/** 设置页（PRD 4.7）：聚集所有可配置项，分组呈现。 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
    onRerunOnboarding: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory(container))

    var autoRestart by remember { mutableStateOf(vm.settings.autoRestart) }
    var keepAlive by remember { mutableStateOf(vm.settings.backgroundKeepAlive) }
    var notifications by remember { mutableStateOf(vm.settings.notificationsEnabled) }
    var keyInput by remember { mutableStateOf("") }
    var masked by remember { mutableStateOf(vm.maskedKey()) }
    var showClearKey by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("设置", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(16.dp))

        // 密钥管理（PRD 4.5）
        Text("密钥管理", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (masked != null) {
            Text("当前密钥：$masked", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("尚未填写 API Key", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text("API Key（sk-…）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = {
                vm.saveKey(keyInput)
                keyInput = ""
                masked = vm.maskedKey()
            }) { Text("保存") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { showClearKey = true }) { Text("清除") }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // 开关项
        SwitchRow("崩溃自动重启", autoRestart) {
            autoRestart = it
            vm.settings.autoRestart = it
        }
        SwitchRow("后台保活", keepAlive) {
            keepAlive = it
            vm.settings.backgroundKeepAlive = it
        }
        SwitchRow("通知", notifications) {
            notifications = it
            vm.settings.notificationsEnabled = it
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // 入口
        OutlinedButton(
            onClick = onRerunOnboarding,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("重新运行权限向导") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showReset = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("重置运行环境") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenLogs,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("日志") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenAbout,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("关于") }
    }

    // 清除密钥二次确认（PRD 5.2 高危操作）
    if (showClearKey) {
        AlertDialog(
            onDismissRequest = { showClearKey = false },
            title = { Text("清除密钥") },
            text = { Text("将删除本机保存的 API Key，需重新输入后才能启动。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearKey()
                    masked = vm.maskedKey()
                    showClearKey = false
                }) { Text("确认清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearKey = false }) { Text("取消") }
            },
        )
    }

    // 重置环境二次确认（PRD 4.2）
    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("重置运行环境") },
            text = { Text("将删除容器内的全部数据、插件与配置，环境回到刚安装状态。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetEnvironment()
                    showReset = false
                }) { Text("确认重置") }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

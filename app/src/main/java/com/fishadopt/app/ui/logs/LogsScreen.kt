package com.fishadopt.app.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishadopt.app.data.LogEntry
import com.fishadopt.app.data.LogLevel
import com.fishadopt.app.di.AppContainer
import com.fishadopt.app.di.AppViewModelFactory
import com.fishadopt.app.ui.theme.LogErrorColor
import com.fishadopt.app.ui.theme.LogInfoColor
import com.fishadopt.app.ui.theme.LogWarningColor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 日志页（PRD 4.6）：实时滚动列表 + 暂停/清空/复制/导出。 */
@Composable
fun LogsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: LogsViewModel = viewModel(factory = AppViewModelFactory(container))
    val context = LocalContext.current
    val entries by vm.entries.collectAsState()
    val listState = rememberLazyListState()

    var paused by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // 自动滚动到底部（暂停时停止）
    LaunchedEffect(entries.size, paused) {
        if (!paused && entries.isNotEmpty()) {
            listState.scrollToItem(entries.size - 1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = onBack) { Text("返回") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { paused = !paused }) { Text(if (paused) "继续" else "暂停") }
            TextButton(onClick = { vm.clear(); message = "已清空" }) { Text("清空") }
            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("logs", vm.exportText()))
                message = "已复制到剪贴板"
            }) { Text("复制") }
            TextButton(onClick = {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                dir.mkdirs()
                val file = File(dir, "fish_log_$ts.txt")
                file.writeText(vm.exportText())
                message = "已导出到 ${file.absolutePath}"
            }) { Text("导出") }
        }

        if (message != null) {
            Text(
                message!!,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (entries.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    "暂无日志",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(entries) { entry ->
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.INFO -> LogInfoColor
        LogLevel.WARNING -> LogWarningColor
        LogLevel.ERROR -> LogErrorColor
    }
    Text(
        text = entry.asLine(),
        color = color,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

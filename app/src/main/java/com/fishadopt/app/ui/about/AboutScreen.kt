package com.fishadopt.app.ui.about

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fishadopt.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 关于页（PRD 4.7 / 9 章）：版本、许可、第三方声明、已知限制、源码与反馈。
 * 许可文本需在应用内可完整查看，不得只放链接。
 */
@Composable
fun AboutScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current

    var notice by remember { mutableStateOf("") }
    var mit by remember { mutableStateOf("") }
    var gpl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            notice = readAsset(context, "licenses/NOTICE.txt")
            mit = readAsset(context, "licenses/MIT.txt")
            gpl = readAsset(context, "licenses/GPLv2.txt")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("关于", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(16.dp))

        Section("产品信息") {
            Text("一键领养小肥鱼", style = MaterialTheme.typography.titleMedium)
            Text("版本号：1.0.0（MVP）", style = MaterialTheme.typography.bodyMedium)
        }

        Section("第三方无关声明") {
            Text(DISCLAIMER, style = MaterialTheme.typography.bodyMedium)
        }

        Section("已知限制") {
            Text("• 后台保活为“尽力而为”，长时间锁屏或内存紧张时服务仍可能被系统结束。", style = MaterialTheme.typography.bodyMedium)
            Text("• 仅支持 arm64-v8a 处理器，系统版本 Android 12–15。", style = MaterialTheme.typography.bodyMedium)
            Text("• 不同厂商系统策略差异可能导致个别机型容器启动失败。", style = MaterialTheme.typography.bodyMedium)
        }

        Section("源码与反馈") {
            Text("源码仓库：https://github.com/fishadopt/fishadopt-app（占位，请替换为实际地址）", style = MaterialTheme.typography.bodyMedium)
            Text("反馈渠道：请通过源码仓库 Issue 提交问题与日志。", style = MaterialTheme.typography.bodyMedium)
        }

        Section("开源组件清单") {
            Text(notice.ifEmpty { "加载中…" }, style = MaterialTheme.typography.bodySmall)
        }

        Section("MIT License") {
            Text(mit.ifEmpty { "加载中…" }, style = MaterialTheme.typography.bodySmall)
        }

        Section("GNU General Public License v2") {
            Text(gpl.ifEmpty { "加载中…" }, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    content()
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
}

private fun readAsset(context: Context, name: String): String = try {
    context.assets.open(name).bufferedReader().use { it.readText() }
} catch (_: Exception) {
    ""
}

private const val DISCLAIMER =
    "本应用为第三方爱好者工具，基于 DeepSeek Harness 二次封装，非 DeepSeek 官方产品，" +
        "不提供任何算力与模型额度。使用本应用产生的模型调用费用由用户自行承担。" +
        "受安卓系统机制限制，后台运行无法保证持续稳定。"

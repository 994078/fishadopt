package com.fishadopt.app.ui.chat

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishadopt.app.di.AppContainer
import com.fishadopt.app.di.AppViewModelFactory
import kotlinx.coroutines.delay

/**
 * 对话界面页（PRD 4.4）：全屏 WebView 承载容器内 DSH 前端，
 * 不改其源码与交互逻辑。处理超时 / 白屏 / 端口错误三类异常。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatScreen(
    container: AppContainer,
    onBackToHome: () -> Unit,
) {
    val vm: ChatViewModel = viewModel(factory = AppViewModelFactory(container))
    val context = LocalContext.current
    val webUrl by vm.webUrl.collectAsState()

    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    val filePathCallback = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) filePathCallback.value?.onReceiveValue(arrayOf(uri))
        else filePathCallback.value?.onReceiveValue(null)
        filePathCallback.value = null
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        loading = false
                        errorMessage = "服务异常，请返回控制页重启服务"
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    callback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?,
                ): Boolean {
                    filePathCallback.value = callback
                    openDocument.launch(arrayOf("*/*"))
                    return true
                }
            }
            setDownloadListener { url, _, _, _, _ ->
                download(context, url)
            }
        }
    }

    // 加载（token 捕获后自动用带 token 地址重载；PRD 4.4 超时提示重试）
    LaunchedEffect(reloadKey, webUrl) {
        loading = true
        errorMessage = null
        webView.loadUrl(webUrl ?: vm.url)
        delay(30_000)
        if (loading) {
            errorMessage = "界面加载超时，请查看日志"
        }
    }

    // 返回键拦截：返回控制页 / 留在当前页（PRD 4.4）
    var showBackDialog by remember { mutableStateOf(false) }
    BackHandler { showBackDialog = true }
    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            title = { Text("离开对话界面") },
            text = { Text("小肥鱼仍在后台运行。") },
            confirmButton = {
                TextButton(onClick = onBackToHome) { Text("返回控制页") }
            },
            dismissButton = {
                TextButton(onClick = { showBackDialog = false }) { Text("留在当前页") }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )

        if (loading && errorMessage == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text("正在加载界面…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(errorMessage!!, style = MaterialTheme.typography.bodyLarge)
                Row {
                    Button(onClick = { reloadKey++ }) { Text("重试") }
                    TextButton(onClick = onBackToHome) { Text("返回控制页") }
                }
            }
        }
    }
}

private fun download(context: Context, url: String) {
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("小肥鱼导出文件")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, Uri.parse(url).lastPathSegment)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    } catch (_: Exception) {
        // 下载失败不影响主流程
    }
}

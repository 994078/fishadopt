package com.fishadopt.app.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishadopt.app.R
import com.fishadopt.app.di.AppContainer
import com.fishadopt.app.di.AppViewModelFactory
import com.fishadopt.app.ui.theme.LogInfoColor

/** 权限向导页（PRD 4.1，可重复进入，每步一句“为什么”）。 */
@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    val vm: OnboardingViewModel = viewModel(factory = AppViewModelFactory(container))
    val context = LocalContext.current

    // 从系统设置页返回后实时刷新授权状态
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    val status by vm.status.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        PermissionRow(
            title = stringResource(R.string.permission_storage_title),
            reason = stringResource(R.string.permission_storage_reason),
            granted = status.storage,
            onGrant = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            },
        )
        Spacer(Modifier.height(16.dp))

        PermissionRow(
            title = stringResource(R.string.permission_notification_title),
            reason = stringResource(R.string.permission_notification_reason),
            granted = status.notification,
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            },
        )
        Spacer(Modifier.height(16.dp))

        PermissionRow(
            title = stringResource(R.string.permission_battery_title),
            reason = stringResource(R.string.permission_battery_reason),
            granted = status.batteryOptimizationIgnored,
            onGrant = {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                }
            },
        )

        Spacer(Modifier.weight(1f))

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.permission_done))
        }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.permission_skip))
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    reason: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(reason, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Text(stringResource(R.string.permission_granted), color = LogInfoColor)
        } else {
            Button(onClick = onGrant) {
                Text(stringResource(R.string.permission_grant))
            }
        }
    }
}

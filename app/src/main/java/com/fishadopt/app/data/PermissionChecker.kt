package com.fishadopt.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * 权限校验（PRD 4.1）。
 * 所有授权状态在每次启动时重新校验，不缓存结果。
 */
class PermissionChecker(private val context: Context) {

    data class Status(
        val storage: Boolean,
        val notification: Boolean,
        val batteryOptimizationIgnored: Boolean,
    ) {
        val allGranted: Boolean get() = storage && notification && batteryOptimizationIgnored
    }

    fun current(): Status = Status(
        storage = hasStorageAccess(),
        notification = hasNotificationPermission(),
        batteryOptimizationIgnored = isIgnoringBatteryOptimizations(),
    )

    /** 所有文件访问权限（MANAGE_EXTERNAL_STORAGE）。 */
    fun hasStorageAccess(): Boolean = Environment.isExternalStorageManager()

    /** 通知权限（Android 13+ 运行时权限，13 以下默认具备）。 */
    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 是否已忽略电池优化（对应“不关闭电池优化极易被清理”）。 */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}

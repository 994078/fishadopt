package com.fishadopt.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API Key 安全存储（PRD 4.5）。
 *
 * 使用系统加密偏好存储（EncryptedSharedPreferences，由 Android Keystore 背书）保存密钥：
 * - 不写明文文件、数据库或 RootFS 内任何配置文件；
 * - 注入仅通过容器启动时的环境变量 [DSH_KEY]，进程结束即失效，不落盘；
 * - 清除时同时移除持久化副本。
 *
 * 注：security-crypto 库已进入维护模式，如需更强保证可替换为 Keystore + AES-GCM 手动实现，
 * 但接口与“系统加密偏好存储”的要求一致。
 */
class KeyStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "fish_adopt_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun saveKey(key: String) {
        prefs.edit().putString(KEY_API, key.trim()).apply()
    }

    fun getKey(): String? = prefs.getString(KEY_API, null)

    /** 掩码展示用：仅保留前 4 位 + 后缀，中间打码。 */
    fun maskedKey(): String? {
        val k = getKey() ?: return null
        if (k.length <= 8) return "****"
        return k.take(4) + "****" + k.takeLast(2)
    }

    fun clear() {
        prefs.edit().remove(KEY_API).apply()
    }

    private companion object {
        const val KEY_API = "api_key"
    }
}

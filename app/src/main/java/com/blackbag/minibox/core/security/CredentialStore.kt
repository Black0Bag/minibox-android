package com.blackbag.minibox.core.security

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.blackbag.minibox.core.model.ConnectionConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Keystore-backed 凭据存储。
 *
 * 规则：不在源码、日志、截图中保存 API Key/设备 token（rules.md）。
 * 使用 EncryptedSharedPreferences（AES-256-GCM），MasterKey 由 Android Keystore 管理。
 *
 * 证据：minibile 已验证 security-crypto 1.1.0-alpha06 可工作。
 */
class CredentialStore(context: Context) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun saveConnectionConfig(config: ConnectionConfig) {
        val encoded = json.encodeToString(config)
        prefs.edit().putString(KEY_CONNECTION_CONFIG, encoded).apply()
    }

    fun loadConnectionConfig(): ConnectionConfig? {
        val raw = prefs.getString(KEY_CONNECTION_CONFIG, null) ?: return null
        return try {
            json.decodeFromString<ConnectionConfig>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode connection config", e)
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val TAG = "CredentialStore"
        const val FILE_NAME = "minibox_credentials"
        const val KEY_CONNECTION_CONFIG = "connection_config"
    }
}

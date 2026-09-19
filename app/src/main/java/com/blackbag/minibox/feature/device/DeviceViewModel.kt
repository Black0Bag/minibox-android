package com.blackbag.minibox.feature.device

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackbag.minibox.core.model.DeviceHelloParams
import com.blackbag.minibox.core.model.DeviceWsState
import com.blackbag.minibox.core.network.DeviceWsClient
import com.blackbag.minibox.core.network.MiniboxHttpClient
import com.blackbag.minibox.core.security.CredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设备连接 UI 状态（websocket.md 实现要求 6：展示目标设备/动作/状态）。
 */
data class DeviceUiState(
    val tokenInput: String = "",
    val deviceId: String = "",
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val stateLabel: String = "未连接",
    val lastRttMs: Long = -1,
    val error: String? = null,
)

/**
 * 设备连接 ViewModel：管理 DeviceWsClient 生命周期。
 *
 * onCleared 断开连接（前台服务是 F3 主体，本任务不做常驻）。
 */
class DeviceViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val credentialStore = CredentialStore(application)

    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    private var wsClient: DeviceWsClient? = null

    init {
        // 预填已存 token（掩码显示交给 UI 层）
        credentialStore.loadDeviceToken()?.let { token ->
            _uiState.value = _uiState.value.copy(tokenInput = token)
        }
    }

    fun updateToken(token: String) {
        _uiState.value = _uiState.value.copy(tokenInput = token)
    }

    fun updateDeviceId(deviceId: String) {
        _uiState.value = _uiState.value.copy(deviceId = deviceId)
    }

    fun connect() {
        val token = _uiState.value.tokenInput.trim()
        if (token.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "请输入设备 token")
            return
        }
        val config = credentialStore.loadConnectionConfig() ?: run {
            _uiState.value = _uiState.value.copy(error = "请先完成连接诊断")
            return
        }

        credentialStore.saveDeviceToken(token)
        _uiState.value = _uiState.value.copy(connecting = true, error = null)

        val client = DeviceWsClient(
            config = config,
            okHttpClient = MiniboxHttpClient.create(config),
            scope = viewModelScope,
        )
        wsClient = client

        // 收集状态与 RTT
        viewModelScope.launch {
            client.state.collect { s ->
                val label = when (s) {
                    is DeviceWsState.Disconnected -> "未连接"
                    is DeviceWsState.Connecting -> "连接中…"
                    is DeviceWsState.Handshaking -> "握手中…"
                    is DeviceWsState.Ready -> "已就绪"
                    is DeviceWsState.Reconnecting -> "重连中（第 ${s.attempt} 次）"
                }
                _uiState.value = _uiState.value.copy(
                    stateLabel = label,
                    connected = s is DeviceWsState.Ready,
                    connecting = s is DeviceWsState.Connecting || s is DeviceWsState.Handshaking,
                )
            }
        }
        viewModelScope.launch {
            client.lastRttMs.collect { rtt ->
                _uiState.value = _uiState.value.copy(lastRttMs = rtt)
            }
        }

        viewModelScope.launch {
            val hello = DeviceHelloParams(
                id = _uiState.value.deviceId.ifBlank { "android-${Build.MODEL}" },
                model = Build.MODEL,
                android = Build.VERSION.RELEASE,
                capabilities = listOf("screen", "input"),
                permissions = emptyMap(),
            )
            try {
                client.connect(token, hello)
                _uiState.value = _uiState.value.copy(error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    connecting = false,
                    error = "连接失败：${e.message}",
                )
            }
        }
    }

    fun disconnect() {
        wsClient?.disconnect()
        _uiState.value = _uiState.value.copy(connected = false, connecting = false, stateLabel = "未连接")
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        wsClient?.disconnect()
        super.onCleared()
    }
}

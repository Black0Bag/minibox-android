package com.blackbag.minibox.feature.connection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackbag.minibox.core.model.ConnectionConfig
import com.blackbag.minibox.core.security.CredentialStore
import com.blackbag.minibox.data.ConnectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 连接诊断 ViewModel。
 *
 * 职责：管理 UI 状态、调用 Repository、持久化凭据。
 * 规则（rules.md）：reducer/ViewModel/Repository 必测。
 */
class ConnectionViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialStore = CredentialStore(application)

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        // 启动时加载已保存的配置
        credentialStore.loadConnectionConfig()?.let { config ->
            _uiState.value = _uiState.value.copy(
                host = config.host,
                port = config.port.toString(),
                token = config.token,
            )
        }
    }

    fun updateHost(host: String) {
        _uiState.value = _uiState.value.copy(host = host)
    }

    fun updatePort(port: String) {
        _uiState.value = _uiState.value.copy(port = port.filter { it.isDigit() })
    }

    fun updateToken(token: String) {
        _uiState.value = _uiState.value.copy(token = token)
    }

    fun diagnose() {
        val state = _uiState.value
        if (!state.canDiagnose) return

        val port = state.port.toIntOrNull() ?: return

        val config = ConnectionConfig(
            host = state.host.trim(),
            port = port,
            token = state.token.trim(),
        )

        // 持久化凭据（EncryptedSharedPreferences）
        credentialStore.saveConnectionConfig(config)

        _uiState.value = _uiState.value.copy(
            isDiagnosing = true,
            health = null,
            ready = null,
            serverStatus = null,
            errors = emptyList(),
        )

        viewModelScope.launch {
            val repository = ConnectionRepository(config)
            val result = repository.diagnose()

            _uiState.value = when (result) {
                is ConnectionRepository.DiagnosticResult.Success -> {
                    _uiState.value.copy(
                        isDiagnosing = false,
                        health = result.health,
                        ready = result.ready,
                        serverStatus = result.serverStatus,
                    )
                }
                is ConnectionRepository.DiagnosticResult.PartialFailure -> {
                    _uiState.value.copy(
                        isDiagnosing = false,
                        health = result.health,
                        ready = result.ready,
                        serverStatus = result.serverStatus,
                        errors = result.errors,
                    )
                }
            }
        }
    }
}

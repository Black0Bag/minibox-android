package com.blackbag.minibox.feature.conversations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackbag.minibox.core.model.Session
import com.blackbag.minibox.core.network.MiniboxHttpClient
import com.blackbag.minibox.core.network.RestClient
import com.blackbag.minibox.core.security.CredentialStore
import com.blackbag.minibox.data.ConversationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 会话列表 UI 状态。
 */
data class ConversationsUiState(
    val loading: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val creating: Boolean = false,
    val error: String? = null,
)

/**
 * 会话列表 ViewModel。
 *
 * 规则（rules.md）：ViewModel 必测；错误语义来自 RestClient.Result。
 */
class ConversationsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val credentialStore = CredentialStore(application)

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    private fun repository(): ConversationsRepository? {
        val config = credentialStore.loadConnectionConfig() ?: return null
        val client = MiniboxHttpClient.create(config)
        return ConversationsRepository(RestClient(client, config))
    }

    fun loadSessions() {
        val repo = repository() ?: run {
            _uiState.value = _uiState.value.copy(error = "未配置连接，请先完成诊断")
            return
        }
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = repo.listSessions()) {
                is RestClient.Result.Ok -> {
                    _uiState.value = _uiState.value.copy(loading = false, sessions = result.value)
                }
                is RestClient.Result.HttpError -> {
                    _uiState.value = _uiState.value.copy(loading = false, error = result.problem.detail)
                }
                is RestClient.Result.Unauthorized -> {
                    _uiState.value = _uiState.value.copy(loading = false, error = "凭据无效（401）")
                }
                is RestClient.Result.NetworkFailure -> {
                    _uiState.value = _uiState.value.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun createSession(onCreated: (String) -> Unit) {
        val repo = repository() ?: return
        _uiState.value = _uiState.value.copy(creating = true)
        viewModelScope.launch {
            when (val result = repo.createSession()) {
                is RestClient.Result.Ok -> {
                    _uiState.value = _uiState.value.copy(creating = false)
                    onCreated(result.value.id)
                }
                is RestClient.Result.HttpError -> {
                    _uiState.value = _uiState.value.copy(creating = false, error = result.problem.detail)
                }
                is RestClient.Result.Unauthorized -> {
                    _uiState.value = _uiState.value.copy(creating = false, error = "凭据无效（401）")
                }
                is RestClient.Result.NetworkFailure -> {
                    _uiState.value = _uiState.value.copy(creating = false, error = result.message)
                }
            }
        }
    }
}

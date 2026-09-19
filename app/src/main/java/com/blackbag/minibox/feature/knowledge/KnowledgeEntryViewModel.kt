package com.blackbag.minibox.feature.knowledge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackbag.minibox.core.network.MiniboxHttpClient
import com.blackbag.minibox.core.network.RestClient
import com.blackbag.minibox.core.security.CredentialStore
import com.blackbag.minibox.data.KnowledgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 条目详情 UI 状态。
 */
data class KnowledgeEntryUiState(
    val loading: Boolean = true,
    val entryId: Long = 0,
    val content: String = "",
    val source: String = "",
    val tags: List<String> = emptyList(),
    val importance: Double = 0.0,
    val mutating: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
    val unauthorized: Boolean = false,
)

/**
 * 条目详情 ViewModel：加载 / 编辑 / 删除。
 *
 * rules.md：ViewModel 必测；错误语义走 RestClient.Result 四态。
 */
class KnowledgeEntryViewModel(
    application: Application,
    private val entryId: Long,
) : AndroidViewModel(application) {

    private val credentialStore = CredentialStore(application)

    private val _uiState = MutableStateFlow(KnowledgeEntryUiState(entryId = entryId))
    val uiState: StateFlow<KnowledgeEntryUiState> = _uiState.asStateFlow()

    private fun repository(): KnowledgeRepository? {
        val config = credentialStore.loadConnectionConfig() ?: return null
        val client = MiniboxHttpClient.create(config)
        return KnowledgeRepository(RestClient(client, config))
    }

    fun load() {
        val repo = repository() ?: run {
            _uiState.value = _uiState.value.copy(loading = false, error = "未配置连接，请先完成诊断")
            return
        }
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = repo.getEntry(entryId)) {
                is RestClient.Result.Ok -> {
                    val e = result.value
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        content = e.content,
                        source = e.source ?: "",
                        tags = e.tags,
                        importance = e.importance,
                    )
                }
                is RestClient.Result.HttpError -> _uiState.value = _uiState.value.copy(
                    loading = false, error = result.problem.detail,
                )
                is RestClient.Result.Unauthorized -> _uiState.value = _uiState.value.copy(
                    loading = false, unauthorized = true,
                )
                is RestClient.Result.NetworkFailure -> _uiState.value = _uiState.value.copy(
                    loading = false, error = result.message,
                )
            }
        }
    }

    fun save(content: String, source: String, tags: List<String>, importance: Double) {
        val repo = repository() ?: return
        _uiState.value = _uiState.value.copy(mutating = true, error = null)
        viewModelScope.launch {
            when (
                val result = repo.updateEntry(
                    entryId,
                    com.blackbag.minibox.core.model.KbUpdateRequest(
                        content = content.trim(),
                        source = source.trim().ifBlank { null },
                        tags = tags,
                        importance = importance,
                    ),
                )
            ) {
                is RestClient.Result.Ok -> _uiState.value = _uiState.value.copy(mutating = false)
                is RestClient.Result.HttpError -> _uiState.value = _uiState.value.copy(
                    mutating = false, error = result.problem.detail,
                )
                is RestClient.Result.Unauthorized -> _uiState.value = _uiState.value.copy(
                    mutating = false, unauthorized = true,
                )
                is RestClient.Result.NetworkFailure -> _uiState.value = _uiState.value.copy(
                    mutating = false, error = result.message,
                )
            }
        }
    }

    fun delete() {
        val repo = repository() ?: return
        _uiState.value = _uiState.value.copy(mutating = true, error = null)
        viewModelScope.launch {
            when (val result = repo.deleteEntry(entryId)) {
                is RestClient.Result.Ok -> {
                    _uiState.value = _uiState.value.copy(mutating = false, deleted = true)
                }
                is RestClient.Result.HttpError -> _uiState.value = _uiState.value.copy(
                    mutating = false, error = result.problem.detail,
                )
                is RestClient.Result.Unauthorized -> _uiState.value = _uiState.value.copy(
                    mutating = false, unauthorized = true,
                )
                is RestClient.Result.NetworkFailure -> _uiState.value = _uiState.value.copy(
                    mutating = false, error = result.message,
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

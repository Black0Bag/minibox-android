package com.blackbag.minibox.feature.knowledge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackbag.minibox.core.model.CompileJob
import com.blackbag.minibox.core.model.KbSearchHit
import com.blackbag.minibox.core.model.KnowledgeEntry
import com.blackbag.minibox.core.network.MiniboxHttpClient
import com.blackbag.minibox.core.network.RestClient
import com.blackbag.minibox.core.security.CredentialStore
import com.blackbag.minibox.data.KnowledgeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 知识库主屏 UI 状态（plan.md F2 后半）。
 *
 * 三 Tab：搜索 / 条目列表（分页） / 编译（提交 + 作业轮询）。
 */
data class KnowledgeUiState(
    // 搜索
    val searchQuery: String = "",
    val searching: Boolean = false,
    val searchResults: List<KbSearchHit> = emptyList(),
    val searched: Boolean = false,
    // 条目列表
    val entries: List<KnowledgeEntry> = emptyList(),
    val loadingEntries: Boolean = false,
    val loadingMore: Boolean = false,
    /** 后端无 total，按返回条数 < limit 判定到底（limit=20） */
    val hasMore: Boolean = true,
    // 条目变更
    val mutating: Boolean = false,
    // 编译
    val compileSource: String = "",
    val compiling: Boolean = false,
    val compileJob: CompileJob? = null,
    // 通用
    val loading: Boolean = true,
    val error: String? = null,
    val unauthorized: Boolean = false,
)

/**
 * 知识库 ViewModel：三 Tab 状态与操作。
 *
 * 编译作业轮询：提交后每 2s 查询一次，ready/failed 终止；上限 90 次（3 分钟）
 * 防止无限轮询（rules.md：重连有界并可取消）。
 */
class KnowledgeViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val credentialStore = CredentialStore(application)

    private val _uiState = MutableStateFlow(KnowledgeUiState())
    val uiState: StateFlow<KnowledgeUiState> = _uiState.asStateFlow()

    private fun repository(): KnowledgeRepository? {
        val config = credentialStore.loadConnectionConfig() ?: return null
        val client = MiniboxHttpClient.create(config)
        return KnowledgeRepository(RestClient(client, config))
    }

    fun loadEntries(reset: Boolean) {
        val repo = repository() ?: run {
            _uiState.value = _uiState.value.copy(loading = false, error = "未配置连接，请先完成诊断")
            return
        }
        val s = _uiState.value
        val offset = if (reset) 0 else s.entries.size
        _uiState.value = if (reset) {
            s.copy(loading = true, loadingEntries = true, error = null)
        } else {
            s.copy(loadingMore = true)
        }
        viewModelScope.launch {
            when (val result = repo.listEntries(offset = offset, limit = PAGE_SIZE)) {
                is RestClient.Result.Ok -> {
                    val fetched = result.value.entries
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        loadingEntries = false,
                        loadingMore = false,
                        entries = if (reset) fetched else _uiState.value.entries + fetched,
                        hasMore = fetched.size >= PAGE_SIZE,
                    )
                }
                is RestClient.Result.HttpError -> _uiState.value = _uiState.value.copy(
                    loading = false, loadingEntries = false, loadingMore = false,
                    error = result.problem.detail,
                )
                is RestClient.Result.Unauthorized -> _uiState.value = _uiState.value.copy(
                    loading = false, loadingEntries = false, loadingMore = false,
                    unauthorized = true,
                )
                is RestClient.Result.NetworkFailure -> _uiState.value = _uiState.value.copy(
                    loading = false, loadingEntries = false, loadingMore = false,
                    error = result.message,
                )
                is RestClient.Result.DecodeFailure -> _uiState.value = _uiState.value.copy(
                    loading = false, loadingEntries = false, loadingMore = false,
                    error = result.message,
                )
            }
        }
    }

    // --- 搜索 ---

    fun updateSearchQuery(q: String) {
        _uiState.value = _uiState.value.copy(searchQuery = q)
    }

    fun search() {
        val repo = repository() ?: return
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return
        _uiState.value = _uiState.value.copy(searching = true, error = null)
        viewModelScope.launch {
            when (val result = repo.search(query, topK = 5)) {
                is RestClient.Result.Ok -> _uiState.value = _uiState.value.copy(
                    searching = false, searched = true, searchResults = result.value.hits,
                )
                is RestClient.Result.HttpError -> _uiState.value = _uiState.value.copy(
                    searching = false, error = result.problem.detail,
                )
                is RestClient.Result.Unauthorized -> _uiState.value = _uiState.value.copy(
                    searching = false, unauthorized = true,
                )
                is RestClient.Result.NetworkFailure -> _uiState.value = _uiState.value.copy(
                    searching = false, error = result.message,
                )
                is RestClient.Result.DecodeFailure -> _uiState.value = _uiState.value.copy(
                    searching = false, error = result.message,
                )
            }
        }
    }

    // --- 条目变更 ---

    fun createEntry(content: String, source: String, tags: List<String>) {
        val repo = repository() ?: return
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        _uiState.value = _uiState.value.copy(mutating = true, error = null)
        viewModelScope.launch {
            val entry = KnowledgeEntry(
                id = 0, // 后端分配；请求体 id 被忽略
                content = trimmed,
                source = source.trim().ifBlank { null },
                tags = tags,
            )
            when (val result = repo.createEntry(entry)) {
                is RestClient.Result.Ok -> {
                    _uiState.value = _uiState.value.copy(mutating = false)
                    loadEntries(reset = true)
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
                is RestClient.Result.DecodeFailure -> _uiState.value = _uiState.value.copy(
                    mutating = false, error = result.message,
                )
            }
        }
    }

    fun updateEntry(id: Long, content: String, source: String, tags: List<String>, importance: Double) {
        val repo = repository() ?: return
        _uiState.value = _uiState.value.copy(mutating = true, error = null)
        viewModelScope.launch {
            val patch = com.blackbag.minibox.core.model.KbUpdateRequest(
                content = content.trim(),
                source = source.trim().ifBlank { null },
                tags = tags,
                importance = importance,
            )
            when (val result = repo.updateEntry(id, patch)) {
                is RestClient.Result.Ok -> {
                    _uiState.value = _uiState.value.copy(mutating = false)
                    loadEntries(reset = true)
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
                is RestClient.Result.DecodeFailure -> _uiState.value = _uiState.value.copy(
                    mutating = false, error = result.message,
                )
            }
        }
    }

    fun deleteEntry(id: Long) {
        val repo = repository() ?: return
        _uiState.value = _uiState.value.copy(mutating = true, error = null)
        viewModelScope.launch {
            when (val result = repo.deleteEntry(id)) {
                is RestClient.Result.Ok -> {
                    _uiState.value = _uiState.value.copy(mutating = false)
                    loadEntries(reset = true)
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
                is RestClient.Result.DecodeFailure -> _uiState.value = _uiState.value.copy(
                    mutating = false, error = result.message,
                )
            }
        }
    }

    // --- 编译 ---

    fun updateCompileSource(src: String) {
        _uiState.value = _uiState.value.copy(compileSource = src)
    }

    fun submitCompile() {
        val repo = repository() ?: return
        val src = _uiState.value.compileSource.trim()
        if (src.isEmpty()) return
        _uiState.value = _uiState.value.copy(compiling = true, error = null, compileJob = null)
        viewModelScope.launch {
            when (val result = repo.compile(src)) {
                is RestClient.Result.Ok -> {
                    _uiState.value = _uiState.value.copy(compiling = false)
                    pollCompileJob(result.value.id)
                }
                is RestClient.Result.HttpError -> _uiState.value = _uiState.value.copy(
                    compiling = false, error = result.problem.detail,
                )
                is RestClient.Result.Unauthorized -> _uiState.value = _uiState.value.copy(
                    compiling = false, unauthorized = true,
                )
                is RestClient.Result.NetworkFailure -> _uiState.value = _uiState.value.copy(
                    compiling = false, error = result.message,
                )
                is RestClient.Result.DecodeFailure -> _uiState.value = _uiState.value.copy(
                    compiling = false, error = result.message,
                )
            }
        }
    }

    /** 有界轮询：2s 间隔，ready/failed 终止，最多 90 次（约 3 分钟） */
    private fun pollCompileJob(jobId: String) {
        val repo = repository() ?: return
        viewModelScope.launch {
            var attempts = 0
            while (attempts < MAX_POLLS) {
                attempts++
                when (val result = repo.getCompileJob(jobId)) {
                    is RestClient.Result.Ok -> {
                        val job = result.value
                        _uiState.value = _uiState.value.copy(compileJob = job)
                        if (job.status == "ready" || job.status == "failed") return@launch
                    }
                    is RestClient.Result.HttpError -> {
                        _uiState.value = _uiState.value.copy(error = result.problem.detail)
                        return@launch
                    }
                    is RestClient.Result.Unauthorized -> {
                        _uiState.value = _uiState.value.copy(unauthorized = true)
                        return@launch
                    }
                    is RestClient.Result.NetworkFailure -> {
                        // 轮询期网络抖动不终止，等下一轮
                    }
                    is RestClient.Result.DecodeFailure -> {
                        // 轮询期解析抖动不终止，等下一轮（与网络抖动同策略，见函数注释）
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
            _uiState.value = _uiState.value.copy(error = "编译作业轮询超时，请稍后在编译页查看状态")
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_POLLS = 90
        const val POLL_INTERVAL_MS = 2_000L
    }
}

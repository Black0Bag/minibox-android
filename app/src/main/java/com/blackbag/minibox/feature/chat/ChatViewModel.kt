package com.blackbag.minibox.feature.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackbag.minibox.core.model.ChatEvent
import com.blackbag.minibox.core.model.Message
import com.blackbag.minibox.core.model.Session
import com.blackbag.minibox.core.network.MiniboxHttpClient
import com.blackbag.minibox.core.network.RestClient
import com.blackbag.minibox.core.network.SseClient
import com.blackbag.minibox.core.security.CredentialStore
import com.blackbag.minibox.data.ChatStreamRepository
import com.blackbag.minibox.data.ConversationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 待审批动作（agent.approval_requested 暂停点）。
 */
data class PendingApproval(
    val runId: String,
    val toolName: String,
)

/**
 * 聊天屏消息项。
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val at: String,
)

/**
 * 聊天 UI 状态。
 *
 * 终态判据（docs/sse.md）：agent.run_finished.state，不看 assistant 消息是否到达。
 */
data class ChatUiState(
    val loadingHistory: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val running: Boolean = false,
    val currentStep: String? = null,
    val approval: PendingApproval? = null,
    val error: String? = null,
    val unauthorized: Boolean = false,
) {
    val canSend: Boolean get() = !running && !loadingHistory && !unauthorized
}

/**
 * 聊天 ViewModel：历史加载 + SSE 事件流驱动的状态机。
 *
 * 事件处理（证据：后端 docs/sse.md 事件序列）：
 * agent.message.user → run_started → step_started/finished
 *   [需审批] approval_requested → 提交 → approval_result
 * → run_finished(state) → message.assistant 或 run_failed
 */
class ChatViewModel(
    application: Application,
    private val sessionId: String,
) : AndroidViewModel(application) {

    private val credentialStore = CredentialStore(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamRepo: ChatStreamRepository? = null
    private var restRepo: ConversationsRepository? = null

    init {
        val config = credentialStore.loadConnectionConfig()
        if (config == null) {
            _uiState.value = _uiState.value.copy(
                loadingHistory = false,
                error = "未配置连接，请返回诊断页",
            )
        } else {
            val client = MiniboxHttpClient.create(config)
            restRepo = ConversationsRepository(RestClient(client, config))
            streamRepo = ChatStreamRepository(SseClient(client, config))
            loadHistory()
            observeStream()
        }
    }

    private fun loadHistory() {
        val repo = restRepo ?: return
        _uiState.value = _uiState.value.copy(loadingHistory = true)
        viewModelScope.launch {
            when (val result = repo.getSession(sessionId)) {
                is RestClient.Result.Ok -> applySession(result.value)
                is RestClient.Result.HttpError -> _uiState.value = _uiState.value.copy(
                    loadingHistory = false,
                    error = result.problem.detail,
                )
                is RestClient.Result.Unauthorized -> _uiState.value = _uiState.value.copy(
                    loadingHistory = false,
                    unauthorized = true,
                )
                is RestClient.Result.NetworkFailure -> _uiState.value = _uiState.value.copy(
                    loadingHistory = false,
                    error = result.message,
                )
            }
        }
    }

    /** 全量覆盖（GapDetected 时刷新完整状态，历史窗口 500 条/会话） */
    private fun applySession(session: Session) {
        _uiState.value = _uiState.value.copy(
            loadingHistory = false,
            messages = session.messages.map { ChatMessage(it.role, it.content, it.at) },
        )
    }

    private fun observeStream() {
        val repo = streamRepo ?: return
        viewModelScope.launch {
            repo.stream(sessionId).collect { event ->
                when (event) {
                    is ChatEvent.UserMessage -> Unit
                    // 用户消息发送时已乐观追加；历史刷新会以服务端为准，不重复插入

                    is ChatEvent.RunStarted -> _uiState.value = _uiState.value.copy(
                        running = true,
                        currentStep = null,
                    )

                    is ChatEvent.Step -> _uiState.value = _uiState.value.copy(
                        currentStep = if (event.finished) null else event.step,
                    )

                    is ChatEvent.ApprovalRequested -> _uiState.value = _uiState.value.copy(
                        approval = PendingApproval(event.runId, event.toolName),
                    )

                    is ChatEvent.ApprovalResult -> _uiState.value = _uiState.value.copy(
                        approval = null,
                    )

                    is ChatEvent.RunFinished -> _uiState.value = _uiState.value.copy(
                        running = false,
                        currentStep = null,
                    )

                    is ChatEvent.AssistantMessage -> {
                        val current = _uiState.value
                        _uiState.value = current.copy(
                            messages = current.messages + ChatMessage(
                                role = "assistant",
                                content = event.content,
                                at = event.envelope.timestamp,
                            ),
                        )
                    }

                    is ChatEvent.RunFailed -> {
                        Log.w(TAG, "run failed: ${event.error}")
                        _uiState.value = _uiState.value.copy(
                            running = false,
                            currentStep = null,
                            error = event.error ?: "运行失败",
                        )
                    }

                    is ChatEvent.GapDetected -> loadHistory()

                    is ChatEvent.Unknown -> Log.d(TAG, "unknown event type=${event.envelope.type}")

                    is ChatEvent.Unauthorized -> _uiState.value = _uiState.value.copy(
                        running = false,
                        unauthorized = true,
                        approval = null,
                    )
                }
            }
        }
    }

    fun sendMessage(content: String) {
        val repo = restRepo ?: return
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return

        // 乐观追加；UserMessage 事件到达时不重复插入
        val now = java.time.OffsetDateTime.now().toString()
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + ChatMessage("user", trimmed, now),
        )

        viewModelScope.launch {
            when (val result = repo.sendMessage(sessionId, trimmed)) {
                is RestClient.Result.Ok -> Unit // run_id 由 SSE 事件驱动 UI
                is RestClient.Result.HttpError -> _uiState.value = _uiState.value.copy(
                    error = result.problem.detail,
                )
                is RestClient.Result.Unauthorized -> _uiState.value = _uiState.value.copy(
                    unauthorized = true,
                )
                is RestClient.Result.NetworkFailure -> _uiState.value = _uiState.value.copy(
                    error = result.message,
                )
            }
        }
    }

    fun submitApproval(approved: Boolean) {
        val repo = restRepo ?: return
        val pending = _uiState.value.approval ?: return
        viewModelScope.launch {
            when (val result = repo.submitApproval(pending.runId, approved)) {
                is RestClient.Result.Ok -> Unit // approval_result 事件清卡片
                is RestClient.Result.HttpError -> _uiState.value = _uiState.value.copy(
                    error = result.problem.detail,
                    approval = null,
                )
                is RestClient.Result.Unauthorized -> _uiState.value = _uiState.value.copy(
                    unauthorized = true,
                    approval = null,
                )
                is RestClient.Result.NetworkFailure -> _uiState.value = _uiState.value.copy(
                    error = result.message,
                    approval = null,
                )
            }
        }
    }

    fun rewind(keep: Int) {
        val repo = restRepo ?: return
        viewModelScope.launch {
            when (val result = repo.rewind(sessionId, keep)) {
                is RestClient.Result.Ok -> loadHistory() // 回退成功后全量刷新
                is RestClient.Result.HttpError -> _uiState.value = _uiState.value.copy(
                    error = result.problem.detail,
                )
                is RestClient.Result.Unauthorized -> _uiState.value = _uiState.value.copy(
                    unauthorized = true,
                )
                is RestClient.Result.NetworkFailure -> _uiState.value = _uiState.value.copy(
                    error = result.message,
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private companion object {
        const val TAG = "ChatViewModel"
    }
}

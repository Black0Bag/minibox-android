package com.blackbag.minibox.data

import android.util.Log
import com.blackbag.minibox.core.model.ChatEvent
import com.blackbag.minibox.core.model.ChatEventMapper
import com.blackbag.minibox.core.model.Envelope
import com.blackbag.minibox.core.network.SseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * SSE 会话流 Repository（证据：后端 docs/sse.md + BACKEND_API.md SSE 章节）。
 *
 * 职责：
 * - 把 SseClient 原始回调转成类型化 ChatEvent Flow
 * - 按 event_id / seq 去重（断线重放场景）
 * - 检测 seq 缺口时发 GapDetected，由 UI 刷新完整状态（历史窗口 500 条/会话）
 * - 自动重连（指数退避 1s→30s），401 停止重连并上抛 Unauthorized
 */
class ChatStreamRepository(
    private val sseClient: SseClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** event_id 去重滑动窗口（重放保护） */
    private val seenEventIds = LinkedHashSet<String>()

    /** 最近处理的 seq；SSE 事件可能无 seq（Live 通道），null 表示尚未见过 */
    private var lastSeq: Long? = null

    fun stream(sessionId: String): Flow<ChatEvent> = callbackFlow {
        var source: okhttp3.sse.EventSource? = null
        var backoffMs = 1_000L
        val reconnectScope = CoroutineScope(Dispatchers.IO)
        var closedByUnauthorized = false

        fun connect() {
            if (closedByUnauthorized) return
            source = sseClient.connect(
                sessionId,
                onEvent = { _, type, data ->
                    val envelope = try {
                        json.decodeFromString<Envelope>(data)
                    } catch (e: Exception) {
                        Log.w(TAG, "SSE envelope parse failed for type=$type: ${e.message}")
                        return@connect
                    }
                    when (accept(envelope)) {
                        Accept.DUPLICATE -> return@connect
                        Accept.GAP -> trySend(ChatEvent.GapDetected)
                        Accept.OK -> Unit
                    }
                    trySend(ChatEventMapper.from(type, envelope))
                },
                onUnauthorized = {
                    Log.w(TAG, "SSE 401 — stop reconnect, surface credential error")
                    closedByUnauthorized = true
                    trySend(ChatEvent.Unauthorized)
                    close()
                },
                onError = { t ->
                    Log.e(TAG, "SSE failure, reconnecting in ${backoffMs}ms", t)
                    reconnectScope.launch {
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                        connect()
                    }
                },
            )
        }

        connect()

        awaitClose {
            closedByUnauthorized = true
            source?.cancel()
            reconnectScope.cancel()
        }
    }

    /** event_id / seq 去重 + 缺口检测 */
    private fun accept(envelope: Envelope): Accept {
        val id = envelope.eventId
        if (id in seenEventIds) return Accept.DUPLICATE
        seenEventIds.add(id)
        if (seenEventIds.size > DEDUP_WINDOW) {
            // LinkedHashSet 保序，移除最早项
            seenEventIds.remove(seenEventIds.first())
        }

        val seq = envelope.seq ?: return Accept.OK
        val prev = lastSeq
        if (prev != null && seq > prev + 1) {
            Log.w(TAG, "SSE seq gap: $prev -> $seq, refresh required")
        }
        // 只前进不回退（重放保护已由 event_id 去重兜底）
        lastSeq = if (prev != null && prev > seq) prev else seq
        if (prev != null && seq > prev + 1) {
            return Accept.GAP
        }
        return Accept.OK
    }

    private enum class Accept { OK, DUPLICATE, GAP }

    private companion object {
        const val TAG = "ChatStreamRepo"
        const val DEDUP_WINDOW = 500
    }
}

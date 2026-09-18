package com.blackbag.minibox.core.network

import android.util.Log
import com.blackbag.minibox.core.model.ConnectionConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * SSE 客户端骨架。
 *
 * 仅工厂级：创建带 Authorization 头的 EventSource，记录 lastEventId 用于重连。
 * 事件解析留 F1（会话）。
 *
 * 证据：BACKEND_API.md SSE 章节
 * - 必须携带 Authorization: Bearer
 * - 重连发送 Last-Event-ID（仍需带 Authorization）
 * - 收到 401 停止重连并上报凭据错误
 * - 忽略 : keepalive 注释
 * - data 是 JSON Envelope，不是纯文本
 */
class SseClient(
    private val client: OkHttpClient,
    private val config: ConnectionConfig,
) {
    private val factory = EventSources.createFactory(client)
    private var currentSource: EventSource? = null
    private var lastEventId: String? = null

    /**
     * 连接 SSE 流。
     *
     * @param sessionId 会话 ID
     * @param onEvent 回调 (eventId, eventType, data)，data 是 Envelope JSON
     * @param onUnauthorized 收到 401 时回调，调用方应停止重连并进入凭据错误状态
     * @param onError 其他错误
     */
    fun connect(
        sessionId: String,
        onEvent: (eventId: String, eventType: String, data: String) -> Unit,
        onUnauthorized: () -> Unit,
        onError: (Throwable: Throwable) -> Unit,
    ): EventSource {
        disconnect()

        val requestBuilder = Request.Builder()
            .url(config.sseUrl(sessionId))
            .addHeader("Authorization", "Bearer ${config.token}")

        lastEventId?.let { id -> requestBuilder.addHeader("Last-Event-ID", id) }

        val request = requestBuilder.build()

        return factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (id != null) {
                    lastEventId = id
                }
                // 忽略 keepalive 注释（type 为 null 或 data 为空）
                if (type != null && data.isNotEmpty()) {
                    onEvent(id ?: "", type, data)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "SSE closed for session=$sessionId")
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                val code = response?.code ?: 0
                if (code == 401) {
                    Log.w(TAG, "SSE received 401, stopping reconnect per protocol")
                    onUnauthorized()
                } else {
                    Log.e(TAG, "SSE failure code=$code", t)
                    onError(t ?: RuntimeException("SSE failure code=$code"))
                }
            }
        }).also { currentSource = it }
    }

    fun disconnect() {
        currentSource?.cancel()
        currentSource = null
    }

    private companion object {
        const val TAG = "SseClient"
    }
}

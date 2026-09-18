package com.blackbag.minibox.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 会话（证据：后端 docs/api.md §2）。
 *
 * 字段统一 lower_snake_case（@SerialName 对齐后端 Go struct tag）；at 均为 RFC3339。
 * 会话列表按 updated_at 倒序返回（服务端保证）。
 */
@Serializable
data class Session(
    val id: String,
    val title: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val messages: List<Message> = emptyList(),
    val mode: String? = null,
)

/**
 * 会话消息（证据：后端 docs/api.md §2）。
 *
 * `run_id` 仅 assistant 消息可能携带；`at` 为 RFC3339。
 */
@Serializable
data class Message(
    val role: String,
    val content: String,
    @SerialName("run_id") val runId: String? = null,
    val at: String,
)

/**
 * POST /conversations/{id}/messages 的响应 data。
 * `answer` 字段当前承载 run_id（字段名保留自同步实现）。
 */
@Serializable
data class SendMessageResult(
    val answer: String,
)

/**
 * POST /conversations/{id}/rewind 的响应 data。
 */
@Serializable
data class RewindResult(
    val ok: Boolean,
)

/**
 * POST /conversations/{id}/messages 的请求体。
 */
@Serializable
data class SendChatMessage(
    val message: String,
)

/**
 * POST /approvals/{run_id} 的响应 data（后端 docs/api.md §11）。
 */
@Serializable
data class ApprovalResult(
    @SerialName("run_id") val runId: String? = null,
    val approved: Boolean? = null,
)

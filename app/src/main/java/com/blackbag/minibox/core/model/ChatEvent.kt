package com.blackbag.minibox.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * SSE 会话事件的类型化模型（证据：后端 docs/sse.md 事件序列）。
 *
 * Envelope.data 是 JsonElement，这里按 event.type 分发解析为强类型。
 * 终态判据是 [AgentRunFinished.state]，不是 assistant 消息是否到达。
 */
sealed interface ChatEvent {

    /** agent.message.user —— 用户消息已入库 */
    data class UserMessage(
        val envelope: Envelope,
    ) : ChatEvent

    /** agent.run_started —— run_id + session_id */
    data class RunStarted(
        val envelope: Envelope,
        val runId: String,
        val sessionId: String?,
    ) : ChatEvent

    /** agent.step_started / agent.step_finished —— run_id + step + state */
    data class Step(
        val envelope: Envelope,
        val runId: String,
        val step: String,
        val state: String?,
        val finished: Boolean,
    ) : ChatEvent

    /** agent.approval_requested —— run_id + tool_name，此处暂停等待用户决定 */
    data class ApprovalRequested(
        val envelope: Envelope,
        val runId: String,
        val toolName: String,
    ) : ChatEvent

    /** agent.approval_result —— run_id + approved + state */
    data class ApprovalResult(
        val envelope: Envelope,
        val runId: String,
        val approved: Boolean,
        val state: String?,
    ) : ChatEvent

    /** agent.run_finished —— run_id + state + steps；state 是唯一终态判据 */
    data class RunFinished(
        val envelope: Envelope,
        val runId: String,
        val state: String,
    ) : ChatEvent

    /** agent.message.assistant —— run_id + content（state=done 且有回答时） */
    data class AssistantMessage(
        val envelope: Envelope,
        val runId: String?,
        val content: String,
    ) : ChatEvent

    /** agent.run_failed —— run_id + error（state=failed 时替代 assistant 消息） */
    data class RunFailed(
        val envelope: Envelope,
        val runId: String,
        val error: String?,
    ) : ChatEvent

    /** 其他未识别类型，保留原始 Envelope 供日志/调试 */
    data class Unknown(
        val envelope: Envelope,
    ) : ChatEvent

    /** SSE 收到 401：停止重连，UI 应引导用户修正凭据 */
    data object Unauthorized : ChatEvent

    /** 检测到 seq 缺口：UI 应重新拉取会话完整状态（历史窗口 500 条/会话） */
    data object GapDetected : ChatEvent
}

/**
 * SSE event.type → 类型化 ChatEvent 的分发器（internal，供 ChatStreamRepository 与测试使用）。
 *
 * 后端事件 data 的字段集合随事件类型不同（run_id/step/state/content/…），
 * 统一用 JsonObject 按需取值，避免为每个事件写冗余 DTO。
 */
internal object ChatEventMapper {

    fun from(type: String, envelope: Envelope): ChatEvent {
        val data = (envelope.data as? JsonObject)?.jsonObject ?: JsonObject(emptyMap())
        return when (type) {
            "agent.message.user" -> ChatEvent.UserMessage(envelope)
            "agent.run_started" -> ChatEvent.RunStarted(
                envelope,
                ChatEventData.runId(data) ?: "",
                ChatEventData.sessionId(data),
            )
            "agent.step_started" -> ChatEvent.Step(
                envelope,
                ChatEventData.runId(data) ?: "",
                ChatEventData.step(data) ?: "",
                ChatEventData.state(data),
                finished = false,
            )
            "agent.step_finished" -> ChatEvent.Step(
                envelope,
                ChatEventData.runId(data) ?: "",
                ChatEventData.step(data) ?: "",
                ChatEventData.state(data),
                finished = true,
            )
            "agent.approval_requested" -> ChatEvent.ApprovalRequested(
                envelope,
                ChatEventData.runId(data) ?: "",
                ChatEventData.toolName(data) ?: "",
            )
            "agent.approval_result" -> ChatEvent.ApprovalResult(
                envelope,
                ChatEventData.runId(data) ?: "",
                ChatEventData.approved(data) ?: false,
                ChatEventData.state(data),
            )
            "agent.run_finished" -> ChatEvent.RunFinished(
                envelope,
                ChatEventData.runId(data) ?: "",
                ChatEventData.state(data) ?: "",
            )
            "agent.message.assistant" -> ChatEvent.AssistantMessage(
                envelope,
                ChatEventData.runId(data),
                ChatEventData.content(data) ?: "",
            )
            "agent.run_failed" -> ChatEvent.RunFailed(
                envelope,
                ChatEventData.runId(data) ?: "",
                ChatEventData.error(data),
            )
            else -> ChatEvent.Unknown(envelope)
        }
    }
}

/**
 * SSE 事件 data 各字段的宽松访问器。
 *
 * 后端事件 data 的字段集合随事件类型不同（run_id/step/state/content/…），
 * 这里统一用 JsonObject 按需取值，避免为每个事件写冗余 DTO。
 * AGENTS.md 禁止 as String 兜底——统一走 optString 语义。
 */
object ChatEventData {

    fun runId(data: JsonObject): String? = str(data, "run_id")

    fun sessionId(data: JsonObject): String? = str(data, "session_id")

    fun step(data: JsonObject): String? = str(data, "step")

    fun state(data: JsonObject): String? = str(data, "state")

    fun toolName(data: JsonObject): String? = str(data, "tool_name")

    fun approved(data: JsonObject): Boolean? {
        val primitive = data["approved"] as? JsonPrimitive ?: return null
        return if (primitive.isString) {
            primitive.content.toBooleanStrictOrNull()
        } else {
            primitive.content.toBooleanStrictOrNull()
        }
    }

    fun content(data: JsonObject): String? = str(data, "content")

    fun error(data: JsonObject): String? = str(data, "error")

    private fun str(data: JsonObject, key: String): String? {
        val primitive = data[key] as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        return primitive.content
    }
}

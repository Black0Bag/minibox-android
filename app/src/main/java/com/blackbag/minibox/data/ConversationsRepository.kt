package com.blackbag.minibox.data

import com.blackbag.minibox.core.model.ApprovalResult
import com.blackbag.minibox.core.model.Message
import com.blackbag.minibox.core.model.RewindResult
import com.blackbag.minibox.core.model.SendChatMessage
import com.blackbag.minibox.core.model.SendMessageResult
import com.blackbag.minibox.core.model.Session
import com.blackbag.minibox.core.network.RestClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 会话 Repository（证据：后端 docs/api.md §2）。
 *
 * - 集合路径保留尾斜杠（docs/api.md §10）
 * - 发送消息异步：立即返回 run_id（data.answer），实际回答走 SSE
 * - 会话 ID 客户端决定：POST messages 的 {id} 不存在会以该 ID 新建
 */
class ConversationsRepository(
    private val rest: RestClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** GET /conversations/ —— updated_at 倒序（服务端保证） */
    suspend fun listSessions(): RestClient.Result<List<Session>> =
        rest.get("/conversations/") { envelope ->
            json.decodeFromJsonElement<List<Session>>(envelope.data)
        }

    /** POST /conversations/ —— 空体新建，返回新 Session */
    suspend fun createSession(): RestClient.Result<Session> =
        rest.post("/conversations/", "{}") { envelope ->
            json.decodeFromJsonElement<Session>(envelope.data)
        }

    /** GET /conversations/{id} —— 单个会话（含消息历史） */
    suspend fun getSession(id: String): RestClient.Result<Session> =
        rest.get("/conversations/${id.segment()}") { envelope ->
            json.decodeFromJsonElement<Session>(envelope.data)
        }

    /** POST /conversations/{id}/messages —— 异步，返回 run_id */
    suspend fun sendMessage(
        sessionId: String,
        content: String,
    ): RestClient.Result<SendMessageResult> {
        val body = json.encodeToString(SendChatMessage(content))
        return rest.post("/conversations/${sessionId.segment()}/messages", body) { envelope ->
            json.decodeFromJsonElement<SendMessageResult>(envelope.data)
        }
    }

    /** POST /conversations/{id}/rewind —— 保留最近 keep 轮 */
    suspend fun rewind(
        sessionId: String,
        keep: Int,
    ): RestClient.Result<RewindResult> =
        rest.post(
            "/conversations/${sessionId.segment()}/rewind",
            """{"keep":$keep}""",
        ) { envelope ->
            json.decodeFromJsonElement<RewindResult>(envelope.data)
        }

    /** POST /approvals/{run_id} —— 审批提交（docs/api.md §11） */
    suspend fun submitApproval(
        runId: String,
        approved: Boolean,
    ): RestClient.Result<ApprovalResult> =
        rest.post("/approvals/${runId.segment()}", """{"approved":$approved}""") { envelope ->
            json.decodeFromJsonElement<ApprovalResult>(envelope.data)
        }

    /**
     * 会话消息按时间正序排列（UI 直接展示）。
     * 服务端返回顺序即时间顺序，这里只做防御性排序说明，不改动数据。
     */
    fun orderedMessages(session: Session): List<Message> = session.messages

    /** 路径段安全化：剥离可能注入的斜杠 */
    private fun String.segment(): String = trim().removePrefix("/").removeSuffix("/")
}

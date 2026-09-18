package com.blackbag.minibox.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话 DTO + SSE 事件映射测试。
 *
 * Fixture 对齐后端 docs/api.md §2 与 docs/sse.md 事件序列（snake_case）。
 */
class SessionSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `session deserializes with messages`() {
        val raw = """
        {
            "id": "sess-001",
            "title": "测试会话",
            "created_at": "2026-09-19T10:00:00Z",
            "updated_at": "2026-09-19T10:05:00Z",
            "mode": "ask",
            "messages": [
                {"role": "user", "content": "你好", "at": "2026-09-19T10:00:00Z"},
                {"role": "assistant", "content": "你好！有什么可以帮你？", "run_id": "run-01", "at": "2026-09-19T10:00:05Z"}
            ]
        }
        """.trimIndent()

        val session = json.decodeFromString<Session>(raw)

        assertEquals("sess-001", session.id)
        assertEquals("测试会话", session.title)
        assertEquals("2026-09-19T10:05:00Z", session.updatedAt)
        assertEquals("ask", session.mode)
        assertEquals(2, session.messages.size)
        assertEquals("user", session.messages[0].role)
        assertEquals("run-01", session.messages[1].runId)
        assertNull(session.messages[0].runId)
    }

    @Test
    fun `session list deserializes`() {
        val raw = """
        [
            {"id": "a", "title": "A", "created_at": "2026-09-19T10:00:00Z", "updated_at": "2026-09-19T10:00:00Z", "messages": []},
            {"id": "b", "title": "B", "created_at": "2026-09-19T11:00:00Z", "updated_at": "2026-09-19T11:00:00Z", "messages": []}
        ]
        """.trimIndent()

        val sessions = json.decodeFromString<List<Session>>(raw)

        assertEquals(2, sessions.size)
        assertEquals("a", sessions[0].id)
    }

    @Test
    fun `send message result parses run_id from answer field`() {
        val raw = """
            {"answer": "run-abc-123"}
        """.trimIndent()

        val result = json.decodeFromString<SendMessageResult>(raw)

        assertEquals("run-abc-123", result.answer)
    }

    @Test
    fun `rewind result parses ok`() {
        val raw = """{"ok": true}"""

        val result = json.decodeFromString<RewindResult>(raw)

        assertTrue(result.ok)
    }

    @Test
    fun `approval result parses`() {
        val raw = """
            {"run_id": "run-01", "approved": true}
        """.trimIndent()

        val result = json.decodeFromString<ApprovalResult>(raw)

        assertEquals("run-01", result.runId)
        assertEquals(true, result.approved)
    }

    // --- ChatEventMapper ---

    private fun envelopeOf(type: String, dataJson: String): Envelope {
        val raw = """
        {
            "spec_version": "1.0",
            "event_id": "01EVT",
            "trace_id": "aabbccdd11223344aabbccdd11223344",
            "seq": 12,
            "timestamp": "2026-09-19 12:00:00",
            "producer": "agent",
            "source": "minibox://session/sess-001",
            "type": "$type",
            "data": $dataJson
        }
        """.trimIndent()
        return json.decodeFromString<Envelope>(raw)
    }

    @Test
    fun `mapper parses run_started`() {
        val envelope = envelopeOf("agent.run_started", """{"run_id": "run-9", "session_id": "s1"}""")

        val event = ChatEventMapper.from("agent.run_started", envelope)

        assertTrue(event is ChatEvent.RunStarted)
        event as ChatEvent.RunStarted
        assertEquals("run-9", event.runId)
        assertEquals("s1", event.sessionId)
    }

    @Test
    fun `mapper parses step started and finished`() {
        val started = ChatEventMapper.from(
            "agent.step_started",
            envelopeOf("agent.step_started", """{"run_id": "r", "step": "planning", "state": "running"}"""),
        )
        val finished = ChatEventMapper.from(
            "agent.step_finished",
            envelopeOf("agent.step_finished", """{"run_id": "r", "step": "planning", "state": "done"}"""),
        )

        assertTrue((started as ChatEvent.Step).step == "planning" && !started.finished)
        assertTrue((finished as ChatEvent.Step).step == "planning" && finished.finished)
    }

    @Test
    fun `mapper parses approval requested with tool name`() {
        val event = ChatEventMapper.from(
            "agent.approval_requested",
            envelopeOf("agent.approval_requested", """{"run_id": "r7", "tool_name": "file.write"}"""),
        )

        assertTrue(event is ChatEvent.ApprovalRequested)
        event as ChatEvent.ApprovalRequested
        assertEquals("r7", event.runId)
        assertEquals("file.write", event.toolName)
    }

    @Test
    fun `mapper parses run_finished state as terminal evidence`() {
        val event = ChatEventMapper.from(
            "agent.run_finished",
            envelopeOf("agent.run_finished", """{"run_id": "r", "state": "done", "steps": 3}"""),
        )

        assertTrue(event is ChatEvent.RunFinished)
        assertEquals("done", (event as ChatEvent.RunFinished).state)
    }

    @Test
    fun `mapper parses assistant message content`() {
        val event = ChatEventMapper.from(
            "agent.message.assistant",
            envelopeOf("agent.message.assistant", """{"run_id": "r", "content": "回答正文"}"""),
        )

        assertTrue(event is ChatEvent.AssistantMessage)
        assertEquals("回答正文", (event as ChatEvent.AssistantMessage).content)
    }

    @Test
    fun `mapper parses run_failed error`() {
        val event = ChatEventMapper.from(
            "agent.run_failed",
            envelopeOf("agent.run_failed", """{"run_id": "r", "error": "LLM timeout"}"""),
        )

        assertTrue(event is ChatEvent.RunFailed)
        assertEquals("LLM timeout", (event as ChatEvent.RunFailed).error)
    }

    @Test
    fun `mapper falls back to Unknown for unrecognized type`() {
        val event = ChatEventMapper.from(
            "system.some_new_event",
            envelopeOf("system.some_new_event", """{"x": 1}"""),
        )

        assertTrue(event is ChatEvent.Unknown)
    }
}

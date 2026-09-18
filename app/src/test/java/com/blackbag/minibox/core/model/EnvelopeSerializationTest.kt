package com.blackbag.minibox.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Envelope 序列化/反序列化测试。
 *
 * Fixture 对齐后端 transport/envelope.go 的 JSON 格式（snake_case）。
 * data 字段在后端是 json.RawMessage，Kotlin 侧用 JsonElement 接收。
 */
class EnvelopeSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `envelope without seq deserializes correctly`() {
        val raw = """
        {
            "spec_version": "1.0",
            "event_id": "01HXYZ1234567890ABCDEF",
            "trace_id": "aabbccdd11223344aabbccdd11223344",
            "timestamp": "2026-09-18 12:00:00",
            "producer": "system",
            "source": "api://health",
            "type": "api.health",
            "data": {"status": "ok", "uptime": "1h0m0s", "degrade": "none"}
        }
        """.trimIndent()

        val envelope = json.decodeFromString<Envelope>(raw)

        assertEquals("1.0", envelope.specVersion)
        assertEquals("01HXYZ1234567890ABCDEF", envelope.eventId)
        assertEquals("aabbccdd11223344aabbccdd11223344", envelope.traceId)
        assertEquals(null, envelope.seq)
        assertEquals("2026-09-18 12:00:00", envelope.timestamp)
        assertEquals("system", envelope.producer)
        assertEquals("api://health", envelope.source)
        assertEquals("api.health", envelope.type)
        assertNotNull(envelope.data)
    }

    @Test
    fun `envelope with seq deserializes correctly`() {
        val raw = """
        {
            "spec_version": "1.0",
            "event_id": "01HXYZ1234567890ABCDEG",
            "trace_id": "aabbccdd11223344aabbccdd11223344",
            "seq": 42,
            "timestamp": "2026-09-18 12:00:01",
            "producer": "system",
            "source": "api://ready",
            "type": "api.ready",
            "data": {"status": "ok", "checks": {"database": true, "llm": true, "wizard": true}}
        }
        """.trimIndent()

        val envelope = json.decodeFromString<Envelope>(raw)

        assertEquals(42, envelope.seq)
    }

    @Test
    fun `ProblemDetail deserializes correctly`() {
        val raw = """
        {
            "type": "not_ready",
            "title": "请求失败",
            "status": 503,
            "detail": "database not initialized",
            "instance": "/api/v1/ready"
        }
        """.trimIndent()

        val pd = json.decodeFromString<ProblemDetail>(raw)

        assertEquals("not_ready", pd.type)
        assertEquals("请求失败", pd.title)
        assertEquals(503, pd.status)
        assertEquals("database not initialized", pd.detail)
        assertEquals("/api/v1/ready", pd.instance)
    }

    @Test
    fun `HealthData deserializes from envelope data`() {
        val raw = """
            {"status": "ok", "uptime": "2h30m", "degrade": "none"}
        """.trimIndent()

        val data = json.decodeFromString<HealthData>(raw)

        assertEquals("ok", data.status)
        assertEquals("2h30m", data.uptime)
        assertEquals("none", data.degrade)
    }

    @Test
    fun `ReadyData deserializes with nested checks`() {
        val raw = """
            {"status": "ok", "checks": {"database": true, "llm": false, "wizard": true}}
        """.trimIndent()

        val data = json.decodeFromString<ReadyData>(raw)

        assertEquals("ok", data.status)
        assertTrue(data.checks.database)
        assertEquals(false, data.checks.llm)
        assertTrue(data.checks.wizard)
    }

    @Test
    fun `ServerStatusData deserializes correctly`() {
        val raw = """
            {"ok": true, "uptime": 123.456}
        """.trimIndent()

        val data = json.decodeFromString<ServerStatusData>(raw)

        assertTrue(data.ok)
        assertEquals(123.456, data.uptime, 0.001)
    }

    @Test
    fun `ConnectionConfig builds correct URLs`() {
        val config = ConnectionConfig(
            host = "192.168.1.100",
            port = 8080,
            token = "test-token",
        )

        assertEquals("http://192.168.1.100:8080/api/v1", config.restBaseUrl)
        assertEquals(
            "http://192.168.1.100:8080/api/v1/stream?session_id=sess123",
            config.sseUrl("sess123"),
        )
    }
}

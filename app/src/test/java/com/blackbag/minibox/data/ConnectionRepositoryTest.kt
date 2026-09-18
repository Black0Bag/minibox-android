package com.blackbag.minibox.data

import com.blackbag.minibox.core.model.ConnectionConfig
import com.blackbag.minibox.core.network.MiniboxHttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ConnectionRepository Mock 测试。
 *
 * 使用 MockWebServer 模拟后端 /health、/ready、/server/status 响应。
 * 验证三步诊断序列的正确性（证据：BACKEND_API.md）。
 */
class ConnectionRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ConnectionRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        // ConnectionConfig 的 host:port 指向 MockWebServer
        // restBaseUrl = http://localhost:PORT/api/v1，MockWebServer 的 path 需匹配
        val config = ConnectionConfig(
            host = server.hostName,
            port = server.port,
            token = "test-token",
        )
        repository = ConnectionRepository(config)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `all three steps succeed returns Success`() = runBlocking {
        // MockWebServer 按入队顺序返回响应
        server.enqueue(MockResponse().setBody(healthEnvelope()))
        server.enqueue(MockResponse().setBody(readyEnvelope()))
        server.enqueue(MockResponse().setBody(serverStatusEnvelope()))

        val result = repository.diagnose()

        assertTrue("Expected Success", result is ConnectionRepository.DiagnosticResult.Success)
        val success = result as ConnectionRepository.DiagnosticResult.Success
        assertEquals("ok", success.health.status)
        assertEquals("ok", success.ready?.status)
        assertTrue(success.serverStatus?.ok == true)
    }

    @Test
    fun `server status 401 returns PartialFailure with Unauthorized error`() = runBlocking {
        server.enqueue(MockResponse().setBody(healthEnvelope()))
        server.enqueue(MockResponse().setBody(readyEnvelope()))
        server.enqueue(MockResponse()
            .setResponseCode(401)
            .setHeader("Content-Type", "application/problem+json")
            .setBody(problemDetail(401, "unauthorized", "token invalid")))

        val result = repository.diagnose()

        assertTrue("Expected PartialFailure", result is ConnectionRepository.DiagnosticResult.PartialFailure)
        val partial = result as ConnectionRepository.DiagnosticResult.PartialFailure
        assertNotNull(partial.health)
        assertNotNull(partial.ready)
        assertNull(partial.serverStatus)
        assertTrue(partial.errors.isNotEmpty())
        val statusError = partial.errors.find { it.step == "server_status" }
        assertNotNull(statusError)
        assertEquals(
            ConnectionRepository.DiagnosticResult.ErrorKind.UNAUTHORIZED,
            statusError?.kind,
        )
    }

    @Test
    fun `network failure on health returns PartialFailure with Network error`() = runBlocking {
        // 关闭服务器模拟网络不可达
        server.shutdown()

        val result = repository.diagnose()

        assertTrue("Expected PartialFailure", result is ConnectionRepository.DiagnosticResult.PartialFailure)
        val partial = result as ConnectionRepository.DiagnosticResult.PartialFailure
        assertNull(partial.health)
        val healthError = partial.errors.find { it.step == "health" }
        assertNotNull(healthError)
        assertEquals(
            ConnectionRepository.DiagnosticResult.ErrorKind.NETWORK,
            healthError?.kind,
        )
    }

    // --- Fixtures ---

    private fun healthEnvelope(): String = """
        {
            "spec_version": "1.0",
            "event_id": "01HEALTH001",
            "trace_id": "aabbccdd11223344aabbccdd11223344",
            "timestamp": "2026-09-18 12:00:00",
            "producer": "system",
            "source": "api://health",
            "type": "api.health",
            "data": {"status": "ok", "uptime": "1h0m0s", "degrade": "none"}
        }
    """.trimIndent()

    private fun readyEnvelope(): String = """
        {
            "spec_version": "1.0",
            "event_id": "01READY0001",
            "trace_id": "aabbccdd11223344aabbccdd11223344",
            "timestamp": "2026-09-18 12:00:01",
            "producer": "system",
            "source": "api://ready",
            "type": "api.ready",
            "data": {"status": "ok", "checks": {"database": true, "llm": true, "wizard": true}}
        }
    """.trimIndent()

    private fun serverStatusEnvelope(): String = """
        {
            "spec_version": "1.0",
            "event_id": "01STATUS001",
            "trace_id": "aabbccdd11223344aabbccdd11223344",
            "timestamp": "2026-09-18 12:00:02",
            "producer": "system",
            "source": "api://server/status",
            "type": "api.server.status",
            "data": {"ok": true, "uptime": 3600.0}
        }
    """.trimIndent()

    private fun problemDetail(status: Int, type: String, detail: String): String = """
        {
            "type": "$type",
            "title": "请求失败",
            "status": $status,
            "detail": "$detail",
            "instance": "/api/v1/server/status"
        }
    """.trimIndent()
}

package com.blackbag.minibox.data

import com.blackbag.minibox.core.model.ConnectionConfig
import com.blackbag.minibox.core.network.MiniboxHttpClient
import com.blackbag.minibox.core.network.RestClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 权限 + 工具 Repository Mock 测试。
 *
 * 验证 PATCH 方法、路径与 Envelope 解析（证据：后端 tools_handlers.go）。
 */
class PermissionsToolsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var rest: RestClient
    private lateinit var permissions: PermissionsRepository
    private lateinit var tools: ToolsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val config = ConnectionConfig(
            host = server.hostName,
            port = server.port,
            token = "test-token",
        )
        rest = RestClient(MiniboxHttpClient.create(config), config)
        permissions = PermissionsRepository(rest)
        tools = ToolsRepository(rest)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun envelope(data: String): String = """
        {
            "spec_version": "1.0",
            "event_id": "01PERM",
            "trace_id": "aabbccdd11223344aabbccdd11223344",
            "timestamp": "2026-09-19 12:00:00",
            "producer": "system",
            "source": "api://test",
            "type": "api.test",
            "data": $data
        }
    """.trimIndent()

    @Test
    fun `get permissions hits trailing slash`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                envelope("""{"mode": "ask", "modes": ["yolo", "accept_edits", "ask", "plan"]}"""),
            ),
        )

        val result = permissions.get()

        assertTrue(result is RestClient.Result.Ok)
        assertEquals("ask", (result as RestClient.Result.Ok).value.mode)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/permissions/", recorded.path)
        assertEquals("GET", recorded.method)
    }

    @Test
    fun `set mode sends PATCH with mode body`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(envelope("""{"mode": "plan"}""")),
        )

        val result = permissions.setMode("plan")

        assertTrue(result is RestClient.Result.Ok)
        assertEquals("plan", (result as RestClient.Result.Ok).value.mode)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/permissions/mode", recorded.path)
        assertEquals("PATCH", recorded.method)
        assertEquals("""{"mode":"plan"}""", recorded.body.readUtf8())
        assertEquals("Bearer test-token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `tools list parses count and tools`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                envelope(
                    """
                    {
                        "count": 1,
                        "tools": [
                            {"name": "file.read", "description": "读文件",
                             "metadata": {"read_only": true, "risk_tier": "low"},
                             "json_schema": {"type": "object"}}
                        ]
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val result = tools.list()

        assertTrue(result is RestClient.Result.Ok)
        val data = (result as RestClient.Result.Ok).value
        assertEquals(1, data.count)
        assertEquals("file.read", data.tools[0].name)
        assertTrue(data.tools[0].metadata.readOnly)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/tools/", recorded.path)
    }

    @Test
    fun `invalid mode surfaces HttpError with detail`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(
                    """{"type":"invalid_mode","title":"请求失败","status":400,"detail":"无效权限模式: ninja"}""",
                ),
        )

        val result = permissions.setMode("ninja")

        assertTrue(result is RestClient.Result.HttpError)
        assertEquals("无效权限模式: ninja", (result as RestClient.Result.HttpError).problem.detail)
    }
}

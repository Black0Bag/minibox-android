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
 * ConversationsRepository Mock 测试。
 *
 * 验证集合路径尾斜杠（docs/api.md §10）、请求体与 Envelope 解析。
 */
class ConversationsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ConversationsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val config = ConnectionConfig(
            host = server.hostName,
            port = server.port,
            token = "test-token",
        )
        repository = ConversationsRepository(RestClient(MiniboxHttpClient.create(config), config))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `list sessions hits trailing slash path`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                    "spec_version": "1.0",
                    "event_id": "01L",
                    "trace_id": "aabbccdd11223344aabbccdd11223344",
                    "timestamp": "2026-09-19 12:00:00",
                    "producer": "system",
                    "source": "api://conversations",
                    "type": "api.conversations.list",
                    "data": [
                        {"id": "s1", "title": "T", "created_at": "2026-09-19T10:00:00Z",
                         "updated_at": "2026-09-19T10:00:00Z", "messages": []}
                    ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.listSessions()

        assertTrue(result is RestClient.Result.Ok)
        val sessions = (result as RestClient.Result.Ok).value
        assertEquals(1, sessions.size)
        assertEquals("s1", sessions[0].id)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/conversations/", recorded.path)
        assertEquals("GET", recorded.method)
        assertEquals("Bearer test-token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `create session posts empty body to trailing slash`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                    "spec_version": "1.0",
                    "event_id": "01C",
                    "trace_id": "aabbccdd11223344aabbccdd11223344",
                    "timestamp": "2026-09-19 12:00:00",
                    "producer": "system",
                    "source": "api://conversations",
                    "type": "api.conversations.created",
                    "data": {"id": "new-1", "title": "", "created_at": "2026-09-19T12:00:00Z",
                             "updated_at": "2026-09-19T12:00:00Z", "messages": []}
                }
                """.trimIndent(),
            ),
        )

        val result = repository.createSession()

        assertTrue(result is RestClient.Result.Ok)
        assertEquals("new-1", (result as RestClient.Result.Ok).value.id)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/conversations/", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("{}", recorded.body.readUtf8())
    }

    @Test
    fun `send message posts json body and parses run_id`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                    "spec_version": "1.0",
                    "event_id": "01M",
                    "trace_id": "aabbccdd11223344aabbccdd11223344",
                    "timestamp": "2026-09-19 12:00:00",
                    "producer": "system",
                    "source": "api://conversations/s1/messages",
                    "type": "api.conversations.message.accepted",
                    "data": {"answer": "run-xyz"}
                }
                """.trimIndent(),
            ),
        )

        val result = repository.sendMessage("s1", "你好")

        assertTrue(result is RestClient.Result.Ok)
        assertEquals("run-xyz", (result as RestClient.Result.Ok).value.answer)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/conversations/s1/messages", recorded.path)
        assertEquals("""{"message":"你好"}""", recorded.body.readUtf8())
    }

    @Test
    fun `submit approval posts approved flag`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                    "spec_version": "1.0",
                    "event_id": "01A",
                    "trace_id": "aabbccdd11223344aabbccdd11223344",
                    "timestamp": "2026-09-19 12:00:00",
                    "producer": "system",
                    "source": "api://approvals/run-1",
                    "type": "api.approvals.submitted",
                    "data": {"run_id": "run-1", "approved": true}
                }
                """.trimIndent(),
            ),
        )

        val result = repository.submitApproval("run-1", approved = true)

        assertTrue(result is RestClient.Result.Ok)
        assertEquals(true, (result as RestClient.Result.Ok).value.approved)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/approvals/run-1", recorded.path)
        assertEquals("""{"approved":true}""", recorded.body.readUtf8())
    }

    @Test
    fun `http 401 surfaces Unauthorized`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"type":"unauthorized","title":"认证失败","status":401,"detail":"bad token"}"""),
        )

        val result = repository.listSessions()

        assertTrue(result is RestClient.Result.Unauthorized)
    }
}

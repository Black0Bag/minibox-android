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
 * KnowledgeRepository Mock 测试（证据：api.md §3 + kb_handlers.go）。
 */
class KnowledgeRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: KnowledgeRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val config = ConnectionConfig(
            host = server.hostName,
            port = server.port,
            token = "test-token",
        )
        repository = KnowledgeRepository(RestClient(MiniboxHttpClient.create(config), config))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun envelope(data: String): String = """
        {
            "spec_version": "1.0",
            "event_id": "01KB",
            "trace_id": "aabbccdd11223344aabbccdd11223344",
            "timestamp": "2026-09-19 12:00:00",
            "producer": "system",
            "source": "api://kb",
            "type": "api.kb.test",
            "data": $data
        }
    """.trimIndent()

    @Test
    fun `search posts query body and parses hits`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                envelope(
                    """
                    {"hits": [
                        {"id": 1, "content": "命中一", "score": 0.9, "match_type": "fts", "tier": "store"},
                        {"id": 2, "content": "命中二", "score": 0.5, "match_type": "vec", "tier": "store"}
                    ]}
                    """.trimIndent(),
                ),
            ),
        )

        val result = repository.search("依赖注入", topK = 2)

        assertTrue(result is RestClient.Result.Ok)
        val hits = (result as RestClient.Result.Ok).value.hits
        assertEquals(2, hits.size)
        assertEquals(0.9, hits[0].score, 0.001)
        assertEquals("fts", hits[0].matchType)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/kb/search", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("""{"query":"依赖注入","top_k":2}""", recorded.body.readUtf8())
    }

    @Test
    fun `list entries sends offset and limit query params`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                envelope("""{"entries": [{"id": 1, "content": "条目一", "tier": "store"}]}"""),
            ),
        )

        val result = repository.listEntries(offset = 20, limit = 20)

        assertTrue(result is RestClient.Result.Ok)
        assertEquals(1, (result as RestClient.Result.Ok).value.entries.size)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/kb/store?offset=20&limit=20", recorded.path)
    }

    @Test
    fun `get entry hits id path`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                envelope(
                    """
                    {"id": 42, "content": "详情内容", "importance": 0.5, "tier": "store",
                     "created_at": "2026-09-19T12:00:00Z", "updated_at": "2026-09-19T12:00:00Z"}
                    """.trimIndent(),
                ),
            ),
        )

        val result = repository.getEntry(42)

        assertTrue(result is RestClient.Result.Ok)
        assertEquals("详情内容", (result as RestClient.Result.Ok).value.content)
        assertEquals("/api/v1/kb/store/42", server.takeRequest().path)
    }

    @Test
    fun `create entry posts entry json`() = runBlocking {
        server.enqueue(MockResponse().setBody(envelope("""{"ok": true}""")))

        val result = repository.createEntry(
            com.blackbag.minibox.core.model.KnowledgeEntry(
                id = 0,
                content = "新条目",
                source = "test.md",
                tags = listOf("a", "b"),
            ),
        )

        assertTrue(result is RestClient.Result.Ok)
        assertTrue((result as RestClient.Result.Ok).value.ok)
        assertEquals("/api/v1/kb/store", server.takeRequest().path)
    }

    @Test
    fun `update entry sends PATCH with editable fields`() = runBlocking {
        server.enqueue(MockResponse().setBody(envelope("""{"ok": true}""")))

        val result = repository.updateEntry(
            7,
            com.blackbag.minibox.core.model.KbUpdateRequest(
                content = "更新内容",
                tags = listOf("x"),
                importance = 0.8,
            ),
        )

        assertTrue(result is RestClient.Result.Ok)
        val recorded = server.takeRequest()
        assertEquals("/api/v1/kb/store/7", recorded.path)
        assertEquals("PATCH", recorded.method)
        assertTrue(recorded.body.readUtf8().contains(""""content":"更新内容""""))
    }

    @Test
    fun `delete entry sends DELETE`() = runBlocking {
        server.enqueue(MockResponse().setBody(envelope("""{"ok": true}""")))

        val result = repository.deleteEntry(9)

        assertTrue(result is RestClient.Result.Ok)
        val recorded = server.takeRequest()
        assertEquals("/api/v1/kb/store/9", recorded.path)
        assertEquals("DELETE", recorded.method)
    }

    @Test
    fun `compile posts source and parses job`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                envelope(
                    """
                    {"id": "job-abc", "source": "text", "status": "pending", "progress": 0,
                     "total": 0, "created_at": "2026-09-19T12:00:00Z", "updated_at": "2026-09-19T12:00:00Z"}
                    """.trimIndent(),
                ),
            ),
        )

        val result = repository.compile("一段很长的文本")

        assertTrue(result is RestClient.Result.Ok)
        assertEquals("pending", (result as RestClient.Result.Ok).value.status)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/kb/compile", recorded.path)
        assertEquals("""{"source":"一段很长的文本"}""", recorded.body.readUtf8())
    }

    @Test
    fun `get compile job hits job id path`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                envelope(
                    """
                    {"id": "job-abc", "source": "text", "status": "ready", "progress": 5, "total": 5}
                    """.trimIndent(),
                ),
            ),
        )

        val result = repository.getCompileJob("job-abc")

        assertTrue(result is RestClient.Result.Ok)
        assertEquals("ready", (result as RestClient.Result.Ok).value.status)
        assertEquals("/api/v1/kb/compile/job-abc", server.takeRequest().path)
    }
}

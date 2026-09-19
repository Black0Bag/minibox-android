package com.blackbag.minibox.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识库 DTO 序列化测试（fixture 对齐后端 memory/store.go + compile.go 的 JSON tag）。
 */
class KnowledgeSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `knowledge entry deserializes with snake_case fields`() {
        val raw = """
        {
            "id": 42,
            "content": "知识内容正文",
            "source": "notes.md",
            "tags": ["kotlin", "android"],
            "importance": 0.75,
            "tier": "store",
            "created_at": "2026-09-19T12:00:00Z",
            "updated_at": "2026-09-19T12:05:00Z"
        }
        """.trimIndent()

        val entry = json.decodeFromString<KnowledgeEntry>(raw)

        assertEquals(42L, entry.id)
        assertEquals("知识内容正文", entry.content)
        assertEquals("notes.md", entry.source)
        assertEquals(listOf("kotlin", "android"), entry.tags)
        assertEquals(0.75, entry.importance, 0.001)
        assertEquals("store", entry.tier)
        assertEquals("2026-09-19T12:00:00Z", entry.createdAt)
    }

    @Test
    fun `kb list data deserializes entries`() {
        val raw = """
        {
            "entries": [
                {"id": 1, "content": "第一条", "tier": "store"},
                {"id": 2, "content": "第二条", "tier": "store"}
            ]
        }
        """.trimIndent()

        val data = json.decodeFromString<KbListData>(raw)

        assertEquals(2, data.entries.size)
        assertEquals("第一条", data.entries[0].content)
    }

    @Test
    fun `search hit deserializes flattened entry plus score and match_type`() {
        val raw = """
        {
            "id": 7,
            "content": "命中内容",
            "tier": "store",
            "score": 0.832,
            "match_type": "both"
        }
        """.trimIndent()

        val hit = json.decodeFromString<KbSearchHit>(raw)

        assertEquals(7L, hit.id)
        assertEquals(0.832, hit.score, 0.001)
        assertEquals("both", hit.matchType)
    }

    @Test
    fun `search request serializes top_k`() {
        val req = KbSearchRequest(query = "依赖注入", topK = 8)
        val encoded = Json.encodeToString(KbSearchRequest.serializer(), req)

        assertTrue(encoded.contains(""""query":"依赖注入""""))
        assertTrue(encoded.contains(""""top_k":8"""))
    }

    @Test
    fun `compile job deserializes all statuses`() {
        val ready = json.decodeFromString<CompileJob>(
            """
            {"id": "job-1", "source": "text", "status": "ready", "progress": 10, "total": 10,
             "created_at": "2026-09-19T12:00:00Z", "updated_at": "2026-09-19T12:01:00Z"}
            """.trimIndent(),
        )
        val failed = json.decodeFromString<CompileJob>(
            """
            {"id": "job-2", "source": "url", "status": "failed", "progress": 3, "total": 12,
             "error": "LLM timeout"}
            """.trimIndent(),
        )

        assertEquals("ready", ready.status)
        assertEquals(10, ready.progress)
        assertNull(ready.error)
        assertEquals("failed", failed.status)
        assertEquals("LLM timeout", failed.error)
    }

    @Test
    fun `update request omits null fields`() {
        val patch = KbUpdateRequest(content = "新内容", importance = 0.9)
        val encoded = Json.encodeToString(KbUpdateRequest.serializer(), patch)

        assertTrue(encoded.contains(""""content":"新内容""""))
        assertTrue(encoded.contains(""""importance":0.9"""))
        // encodeDefaults=false 默认不输出显式默认值；null 字段不出现
        assertTrue(!encoded.contains("tags"))
        assertTrue(!encoded.contains("source"))
    }
}

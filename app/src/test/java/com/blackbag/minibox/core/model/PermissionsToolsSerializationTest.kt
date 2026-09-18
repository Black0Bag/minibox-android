package com.blackbag.minibox.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 权限 + 工具 DTO 序列化测试。
 *
 * Fixture 对齐后端 tools_handlers.go 与 domain/tools/tool.go 的 JSON tag（snake_case）。
 */
class PermissionsToolsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `permissions data deserializes`() {
        val raw = """
            {"mode": "ask", "modes": ["yolo", "accept_edits", "ask", "plan"]}
        """.trimIndent()

        val data = json.decodeFromString<PermissionsData>(raw)

        assertEquals("ask", data.mode)
        assertEquals(4, data.modes.size)
        assertTrue(data.modes.contains("yolo"))
    }

    @Test
    fun `tool metadata deserializes with snake_case fields`() {
        val raw = """
        {
            "read_only": true,
            "destructive": false,
            "concurrency_safe": true,
            "search_or_read": true,
            "open_world": false,
            "max_result_size": 4096,
            "risk_tier": "low",
            "requires_approval": false
        }
        """.trimIndent()

        val meta = json.decodeFromString<ToolMetadata>(raw)

        assertTrue(meta.readOnly)
        assertFalse(meta.destructive)
        assertTrue(meta.concurrencySafe)
        assertEquals(4096, meta.maxResultSize)
        assertEquals("low", meta.riskTier)
        assertFalse(meta.requiresApproval)
    }

    @Test
    fun `tools data deserializes with nested tool info`() {
        val raw = """
        {
            "count": 2,
            "tools": [
                {
                    "name": "file.read",
                    "description": "读取文件",
                    "metadata": {
                        "read_only": true,
                        "risk_tier": "low",
                        "requires_approval": false
                    },
                    "json_schema": {"type": "object", "properties": {"path": {"type": "string"}}}
                },
                {
                    "name": "shell.exec",
                    "description": "执行命令",
                    "metadata": {
                        "read_only": false,
                        "destructive": true,
                        "risk_tier": "high",
                        "requires_approval": true
                    },
                    "json_schema": {"type": "object"}
                }
            ]
        }
        """.trimIndent()

        val data = json.decodeFromString<ToolsData>(raw)

        assertEquals(2, data.count)
        assertEquals(2, data.tools.size)

        val fileRead = data.tools[0]
        assertEquals("file.read", fileRead.name)
        assertTrue(fileRead.metadata.readOnly)
        assertEquals("low", fileRead.metadata.riskTier)
        assertFalse(fileRead.metadata.requiresApproval)

        val shellExec = data.tools[1]
        assertTrue(shellExec.metadata.destructive)
        assertEquals("high", shellExec.metadata.riskTier)
        assertTrue(shellExec.metadata.requiresApproval)
    }

    @Test
    fun `tool metadata defaults tolerate missing fields`() {
        val raw = """
            {"name": "minimal", "metadata": {}}
        """.trimIndent()

        val data = json.decodeFromString<ToolsData>(
            """{"count": 1, "tools": [$raw]}""",
        )

        val tool = data.tools[0]
        assertEquals("minimal", tool.name)
        assertEquals("low", tool.metadata.riskTier) // 默认值
        assertFalse(tool.metadata.requiresApproval)
    }
}

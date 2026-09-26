package com.blackbag.minibox.feature.settings

import com.blackbag.minibox.core.model.PermissionsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归（f4-integration 联调缺陷 Bug#2）：
 * PATCH /permissions/mode 只回 {mode}（api.md §5），若用响应整体覆盖
 * PermissionsData，modes 被空默认值冲掉，四个权限模式 chip 全部消失。
 * modes 的事实源是 GET /permissions/ 的响应。
 */
class ApplyModePatchTest {

    private val allModes = listOf("yolo", "accept_edits", "ask", "plan")

    @Test
    fun `patch without modes keeps modes from previous GET`() {
        val previous = PermissionsData(mode = "plan", modes = allModes)
        val patched = PermissionsData(mode = "ask") // PATCH 契约不含 modes 字段 → 空默认
        val merged = applyModePatch(previous, patched)
        assertEquals("ask", merged.mode)
        assertEquals(allModes, merged.modes)
    }

    @Test
    fun `patch carrying modes wins over previous`() {
        val previous = PermissionsData(mode = "plan", modes = allModes)
        val patched = PermissionsData(mode = "yolo", modes = listOf("yolo"))
        assertEquals(patched, applyModePatch(previous, patched))
    }

    @Test
    fun `null previous keeps patch as is`() {
        val patched = PermissionsData(mode = "ask")
        val merged = applyModePatch(null, patched)
        assertEquals(patched, merged)
        assertTrue(merged.modes.isEmpty())
    }
}

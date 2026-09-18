package com.blackbag.minibox.data

import com.blackbag.minibox.core.model.PermissionsData
import com.blackbag.minibox.core.model.ToolsData
import com.blackbag.minibox.core.network.RestClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 权限模式 Repository（证据：后端 tools_handlers.go handlePermissionsGet/Mode）。
 *
 * GET /permissions/ → {mode, modes[]}；PATCH /permissions/mode → {mode}。
 */
class PermissionsRepository(
    private val rest: RestClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(): RestClient.Result<PermissionsData> =
        rest.get("/permissions/") { envelope ->
            json.decodeFromJsonElement<PermissionsData>(envelope.data)
        }

    /**
     * 切换权限模式。非法模式由后端 400 invalid_mode 拒绝。
     * yolo 高风险确认由 UI 层负责。
     */
    suspend fun setMode(mode: String): RestClient.Result<PermissionsData> =
        rest.patch("/permissions/mode", """{"mode":"$mode"}""") { envelope ->
            json.decodeFromJsonElement<PermissionsData>(envelope.data)
        }
}

/**
 * 工具列表 Repository（证据：后端 handleToolList）。
 *
 * 前端动态展示 requires_approval/risk_tier/read_only，不硬编码工具元数据（api.md §4）。
 */
class ToolsRepository(
    private val rest: RestClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(): RestClient.Result<ToolsData> =
        rest.get("/tools/") { envelope ->
            json.decodeFromJsonElement<ToolsData>(envelope.data)
        }
}

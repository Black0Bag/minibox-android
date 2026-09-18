package com.blackbag.minibox.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 权限模式配置（证据：后端 internal/app/tools_handlers.go handlePermissionsGet）。
 *
 * data = {mode, modes[]}；有效模式 yolo / accept_edits / ask / plan。
 * yolo 为高风险模式，前端必须给出明显警示（api.md §5）。
 */
@Serializable
data class PermissionsData(
    val mode: String,
    val modes: List<String> = emptyList(),
)

/**
 * 工具元数据（证据：后端 internal/domain/tools/tool.go Metadata struct）。
 */
@Serializable
data class ToolMetadata(
    @SerialName("read_only") val readOnly: Boolean = false,
    val destructive: Boolean = false,
    @SerialName("concurrency_safe") val concurrencySafe: Boolean = false,
    @SerialName("search_or_read") val searchOrRead: Boolean = false,
    @SerialName("open_world") val openWorld: Boolean = false,
    @SerialName("max_result_size") val maxResultSize: Int = 0,
    @SerialName("risk_tier") val riskTier: String = "low",
    @SerialName("requires_approval") val requiresApproval: Boolean = false,
)

/**
 * 工具条目（证据：后端 handleToolList 的 toolView）。
 *
 * json_schema 是任意 JSON Schema，保留 JsonElement 供展示，不做强类型。
 */
@Serializable
data class ToolInfo(
    val name: String,
    val description: String = "",
    val metadata: ToolMetadata = ToolMetadata(),
    @SerialName("json_schema") val jsonSchema: JsonElement? = null,
)

/**
 * GET /tools/ 响应 data：{count, tools[]}。
 */
@Serializable
data class ToolsData(
    val count: Int = 0,
    val tools: List<ToolInfo> = emptyList(),
)

package com.blackbag.minibox.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 知识库条目（证据：后端 internal/domain/memory/store.go Entry struct）。
 *
 * 双区制：tier=store 存储区 / tier=cache 缓存区（带 TTL）。
 * 前端列表固定展示存储区（后端 kb_handlers.go List 硬编码 TierStore）。
 */
@Serializable
data class KnowledgeEntry(
    val id: Long,
    val content: String,
    val source: String? = null,
    val tags: List<String> = emptyList(),
    val importance: Double = 0.0,
    val tier: String = "store",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * 检索命中（证据：后端 Hit struct —— Go 内嵌 Entry，JSON 平铺）。
 */
@Serializable
data class KbSearchHit(
    val id: Long,
    val content: String,
    val source: String? = null,
    val tags: List<String> = emptyList(),
    val importance: Double = 0.0,
    val tier: String = "store",
    val score: Double = 0.0,
    @SerialName("match_type") val matchType: String = "fts",
)

/**
 * POST /kb/search 的请求体（api.md §3）。
 */
@Serializable
data class KbSearchRequest(
    val query: String,
    @SerialName("top_k") val topK: Int = 5,
)

/**
 * POST /kb/search 的响应 data。
 */
@Serializable
data class KbSearchData(
    val hits: List<KbSearchHit> = emptyList(),
)

/**
 * GET /kb/store?offset=&limit= 的响应 data。
 */
@Serializable
data class KbListData(
    val entries: List<KnowledgeEntry> = emptyList(),
)

/**
 * POST/PATCH 操作的通用响应 data：{ok: true}。
 */
@Serializable
data class KbOkResult(
    val ok: Boolean = false,
)

/**
 * 编译作业（证据：后端 compile.go CompileJob + JobStatus 四态）。
 */
@Serializable
data class CompileJob(
    val id: String,
    val source: String = "",
    val status: String = "pending",
    val progress: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * POST /kb/compile 的请求体。
 */
@Serializable
data class KbCompileRequest(
    val source: String,
)

/**
 * PATCH /kb/store/{id} 的请求体（仅可编辑字段）。
 */
@Serializable
data class KbUpdateRequest(
    val content: String? = null,
    val source: String? = null,
    val tags: List<String>? = null,
    val importance: Double? = null,
)

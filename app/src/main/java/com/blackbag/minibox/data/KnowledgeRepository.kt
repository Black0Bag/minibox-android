package com.blackbag.minibox.data

import com.blackbag.minibox.core.model.CompileJob
import com.blackbag.minibox.core.model.KbCompileRequest
import com.blackbag.minibox.core.model.KbListData
import com.blackbag.minibox.core.model.KbOkResult
import com.blackbag.minibox.core.model.KbSearchData
import com.blackbag.minibox.core.model.KbSearchRequest
import com.blackbag.minibox.core.model.KbUpdateRequest
import com.blackbag.minibox.core.model.KnowledgeEntry
import com.blackbag.minibox.core.network.RestClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 知识库 Repository（证据：api.md §3 + internal/app/kb_handlers.go）。
 *
 * 计划范围（plan.md F2 后半）：搜索、分页列表、条目 CRUD、编译作业轮询。
 * 列表固定存储区（后端 List 硬编码 TierStore）；limit 后端上限 100 默认 20。
 */
class KnowledgeRepository(
    private val rest: RestClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** POST /kb/search —— 三级降级检索（hybrid→fts→like） */
    suspend fun search(
        query: String,
        topK: Int = 5,
    ): RestClient.Result<KbSearchData> {
        val body = json.encodeToString(KbSearchRequest(query = query, topK = topK))
        return rest.post("/kb/search", body) { envelope ->
            json.decodeFromJsonElement<KbSearchData>(envelope.data)
        }
    }

    /** GET /kb/store?offset=&limit= —— 分页列表（存储区） */
    suspend fun listEntries(
        offset: Int = 0,
        limit: Int = 20,
    ): RestClient.Result<KbListData> =
        rest.get("/kb/store?offset=$offset&limit=$limit") { envelope ->
            json.decodeFromJsonElement<KbListData>(envelope.data)
        }

    /** GET /kb/store/{id} —— 条目详情 */
    suspend fun getEntry(id: Long): RestClient.Result<KnowledgeEntry> =
        rest.get("/kb/store/$id") { envelope ->
            json.decodeFromJsonElement<KnowledgeEntry>(envelope.data)
        }

    /** POST /kb/store —— 新建条目（content 必填） */
    suspend fun createEntry(entry: KnowledgeEntry): RestClient.Result<KbOkResult> =
        rest.post("/kb/store", json.encodeToString(entry)) { envelope ->
            json.decodeFromJsonElement<KbOkResult>(envelope.data)
        }

    /** PATCH /kb/store/{id} —— 更新可编辑字段 */
    suspend fun updateEntry(
        id: Long,
        patch: KbUpdateRequest,
    ): RestClient.Result<KbOkResult> =
        rest.patch("/kb/store/$id", json.encodeToString(patch)) { envelope ->
            json.decodeFromJsonElement<KbOkResult>(envelope.data)
        }

    /** DELETE /kb/store/{id} —— 删除条目 */
    suspend fun deleteEntry(id: Long): RestClient.Result<KbOkResult> =
        rest.delete("/kb/store/$id") { envelope ->
            json.decodeFromJsonElement<KbOkResult>(envelope.data)
        }

    /** POST /kb/compile —— 提交编译作业（source=文本或 URL） */
    suspend fun compile(source: String): RestClient.Result<CompileJob> {
        val body = json.encodeToString(KbCompileRequest(source = source))
        return rest.post("/kb/compile", body) { envelope ->
            json.decodeFromJsonElement<CompileJob>(envelope.data)
        }
    }

    /** GET /kb/compile/{job_id} —— 轮询编译作业状态 */
    suspend fun getCompileJob(jobId: String): RestClient.Result<CompileJob> =
        rest.get("/kb/compile/${jobId.trim()}") { envelope ->
            json.decodeFromJsonElement<CompileJob>(envelope.data)
        }
}

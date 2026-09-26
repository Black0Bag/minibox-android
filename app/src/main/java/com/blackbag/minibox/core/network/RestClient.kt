package com.blackbag.minibox.core.network

import android.util.Log
import com.blackbag.minibox.core.model.ConnectionConfig
import com.blackbag.minibox.core.model.Envelope
import com.blackbag.minibox.core.model.HealthData
import com.blackbag.minibox.core.model.ProblemDetail
import com.blackbag.minibox.core.model.ReadyData
import com.blackbag.minibox.core.model.ServerStatusData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * REST 客户端。调用 /health、/ready、/server/status。
 *
 * 设计：先解析 Envelope（data 为 JsonElement），再按 type 字段分发到具体 DTO。
 * 证据：BACKEND_API.md + 后端 transport/envelope.go。
 *
 * 错误处理：收到 401 不自动重试，直接返回 ConnectionError.Unauthorized（BACKEND_API.md）。
 */
class RestClient(
    private val client: OkHttpClient,
    private val config: ConnectionConfig,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val errorHandler = ErrorResponseHandler(json)

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class HttpError(val problem: ProblemDetail) : Result<Nothing>
        data class Unauthorized(val problem: ProblemDetail) : Result<Nothing>
        data class NetworkFailure(val message: String) : Result<Nothing>

        /**
         * 响应解析失败（200 但信封/数据不合法）。
         * rules.md：网络、认证、限流、解析、业务失败必须是独立错误类别，
         * 解析失败不得崩溃（f4-integration 联调缺陷 Bug#1：后端返 null 数组时
         * 未捕获的 SerializationException 直接杀进程）。
         */
        data class DecodeFailure(val message: String) : Result<Nothing>
    }

    suspend fun getHealth(): Result<HealthData> = execute("/health") { envelope ->
        json.decodeFromJsonElement<HealthData>(envelope.data)
    }

    suspend fun getReady(): Result<ReadyData> = execute("/ready") { envelope ->
        json.decodeFromJsonElement<ReadyData>(envelope.data)
    }

    suspend fun getServerStatus(): Result<ServerStatusData> = execute("/server/status") { envelope ->
        json.decodeFromJsonElement<ServerStatusData>(envelope.data)
    }

    /**
     * 通用 GET。路径基于 restBaseUrl（已含 /api/v1）。
     * 集合路径调用方负责保留尾斜杠（后端 docs/api.md §10）。
     */
    suspend fun <T> get(
        path: String,
        decoder: (Envelope) -> T,
    ): Result<T> = execute(path, decoder)

    /**
     * 通用 POST。bodyJson 为请求体 JSON 文本（空体传 "{}"）。
     */
    suspend fun <T> post(
        path: String,
        bodyJson: String,
        decoder: (Envelope) -> T,
    ): Result<T> = doWithBody(path, bodyJson, "POST", decoder)

    /**
     * 通用 PATCH。bodyJson 为请求体 JSON 文本。
     */
    suspend fun <T> patch(
        path: String,
        bodyJson: String,
        decoder: (Envelope) -> T,
    ): Result<T> = doWithBody(path, bodyJson, "PATCH", decoder)

    /**
     * 通用 DELETE（空体）。
     */
    suspend fun <T> delete(
        path: String,
        decoder: (Envelope) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${config.restBaseUrl}$path")
            .delete()
            .build()

        doCall(path, request, decoder)
    }

    private suspend fun <T> doWithBody(
        path: String,
        bodyJson: String,
        method: String,
        decoder: (Envelope) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        val body = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("${config.restBaseUrl}$path")
            .method(method, body)
            .build()

        doCall(path, request, decoder)
    }

    private suspend fun <T> execute(
        path: String,
        decoder: (Envelope) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${config.restBaseUrl}$path")
            .get()
            .build()

        doCall(path, request, decoder)
    }

    private fun <T> doCall(
        path: String,
        request: Request,
        decoder: (Envelope) -> T,
    ): Result<T> {
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.e(TAG, "Network failure for $path", e)
            return Result.NetworkFailure(e.message ?: "网络连接失败")
        }

        // use 是 inline：lambda 返回值即为 use 的返回值，这里显式 return
        return response.use {
            if (it.isSuccessful) {
                val body = it.body?.string() ?: return Result.NetworkFailure("空响应体")
                val envelope = try {
                    json.decodeFromString<Envelope>(body)
                } catch (e: Exception) {
                    Log.e(TAG, "响应信封解析失败: $path", e)
                    return Result.DecodeFailure("响应解析失败: ${e.message}")
                }
                val decoded = try {
                    decoder(envelope)
                } catch (e: Exception) {
                    Log.e(TAG, "响应数据解析失败: $path", e)
                    return Result.DecodeFailure("响应数据解析失败: ${e.message}")
                }
                Result.Ok(decoded)
            } else {
                val error = errorHandler.parse(it)
                when {
                    it.code == 401 -> {
                        val pd = (error as? ErrorResponseHandler.ApiError.Problem)?.detail
                            ?: ProblemDetail("unauthorized", "认证失败", 401, "")
                        Result.Unauthorized(pd)
                    }
                    error is ErrorResponseHandler.ApiError.Problem -> Result.HttpError(error.detail)
                    else -> Result.HttpError(
                        ProblemDetail("unknown", "未知错误", it.code, error.toString())
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "RestClient"
    }
}

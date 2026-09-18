package com.blackbag.minibox.core.network

import android.util.Log
import com.blackbag.minibox.core.model.Envelope
import com.blackbag.minibox.core.model.HealthData
import com.blackbag.minibox.core.model.ProblemDetail
import com.blackbag.minibox.core.model.ReadyData
import com.blackbag.minibox.core.model.ServerStatusData
import kotlinx.serialization.json.Json

/**
 * RFC 7807 错误响应解析器。
 *
 * 后端全局统一：401/404/405/500/503 等错误都是 application/problem+json + 五字段。
 * 证据：BACKEND_API.md「错误格式全局统一」章节。
 */
class ErrorResponseHandler(private val json: Json) {

    fun parse(response: okhttp3.Response): ApiError {
        val body = response.body?.string() ?: ""
        return if (response.headers["Content-Type"]?.contains("application/problem+json") == true ||
            body.contains("\"type\"") && body.contains("\"title\"") && body.contains("\"status\"")) {
            try {
                val pd = json.decodeFromString<ProblemDetail>(body)
                ApiError.Problem(pd)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse ProblemDetail from body", e)
                ApiError.Unknown(response.code, body)
            }
        } else {
            ApiError.Unknown(response.code, body)
        }
    }

    sealed interface ApiError {
        data class Problem(val detail: ProblemDetail) : ApiError
        data class Unknown(val code: Int, val rawBody: String) : ApiError
    }

    private companion object {
        const val TAG = "ErrorResponseHandler"
    }
}

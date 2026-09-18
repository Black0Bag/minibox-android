package com.blackbag.minibox.data

import android.util.Log
import com.blackbag.minibox.core.model.ConnectionConfig
import com.blackbag.minibox.core.model.HealthData
import com.blackbag.minibox.core.model.ProblemDetail
import com.blackbag.minibox.core.model.ReadyData
import com.blackbag.minibox.core.model.ServerStatusData
import com.blackbag.minibox.core.network.MiniboxHttpClient
import com.blackbag.minibox.core.network.RestClient

/**
 * 连接诊断 Repository。
 *
 * 三步序列（证据：BACKEND_API.md 端点表 + 后端 health_handlers.go）：
 * 1. /health（免认证）→ liveness
 * 2. /ready（免认证）→ readiness（DB/LLM/wizard）
 * 3. /server/status（Bearer）→ 认证验证
 *
 * 每步独立判定，失败不中断后续步骤（用户可能需要看到哪个步骤失败）。
 * 但 401 在步骤 3 返回时标记为凭据错误（不自动重试，BACKEND_API.md）。
 */
class ConnectionRepository(
    private val config: ConnectionConfig,
) {
    private val client = MiniboxHttpClient.create(config)
    private val restClient = RestClient(client, config)

    sealed interface DiagnosticResult {
        data class Success(
            val health: HealthData,
            val ready: ReadyData?,
            val serverStatus: ServerStatusData?,
        ) : DiagnosticResult

        data class PartialFailure(
            val health: HealthData?,
            val ready: ReadyData?,
            val serverStatus: ServerStatusData?,
            val errors: List<StepError>,
        ) : DiagnosticResult

        data class StepError(
            val step: String,
            val kind: ErrorKind,
            val detail: String,
        )

        enum class ErrorKind { NETWORK, UNAUTHORIZED, HTTP_ERROR }
    }

    suspend fun diagnose(): DiagnosticResult {
        val errors = mutableListOf<DiagnosticResult.StepError>()

        // Step 1: /health
        val healthResult = restClient.getHealth()
        val health = when (healthResult) {
            is RestClient.Result.Ok -> healthResult.value
            is RestClient.Result.NetworkFailure -> {
                errors.add(
                    DiagnosticResult.StepError(
                        step = "health",
                        kind = DiagnosticResult.ErrorKind.NETWORK,
                        detail = healthResult.message,
                    )
                )
                Log.w(TAG, "Health check network failure: ${healthResult.message}")
                null
            }
            is RestClient.Result.HttpError -> {
                errors.add(
                    DiagnosticResult.StepError(
                        step = "health",
                        kind = DiagnosticResult.ErrorKind.HTTP_ERROR,
                        detail = "${healthResult.problem.title}: ${healthResult.problem.detail}",
                    )
                )
                Log.w(TAG, "Health check HTTP error: ${healthResult.problem}")
                null
            }
            is RestClient.Result.Unauthorized -> {
                // /health 不应该返回 401（免认证），但如果发生说明配置有误
                errors.add(
                    DiagnosticResult.StepError(
                        step = "health",
                        kind = DiagnosticResult.ErrorKind.UNAUTHORIZED,
                        detail = healthResult.problem.detail,
                    )
                )
                Log.w(TAG, "Health check unexpected 401: ${healthResult.problem}")
                null
            }
        }

        // Step 2: /ready
        val readyResult = restClient.getReady()
        val ready = when (readyResult) {
            is RestClient.Result.Ok -> readyResult.value
            is RestClient.Result.NetworkFailure -> {
                errors.add(
                    DiagnosticResult.StepError(
                        step = "ready",
                        kind = DiagnosticResult.ErrorKind.NETWORK,
                        detail = readyResult.message,
                    )
                )
                Log.w(TAG, "Ready check network failure: ${readyResult.message}")
                null
            }
            is RestClient.Result.HttpError -> {
                errors.add(
                    DiagnosticResult.StepError(
                        step = "ready",
                        kind = DiagnosticResult.ErrorKind.HTTP_ERROR,
                        detail = "${readyResult.problem.title}: ${readyResult.problem.detail}",
                    )
                )
                Log.w(TAG, "Ready check HTTP error: ${readyResult.problem}")
                null
            }
            is RestClient.Result.Unauthorized -> {
                errors.add(
                    DiagnosticResult.StepError(
                        step = "ready",
                        kind = DiagnosticResult.ErrorKind.UNAUTHORIZED,
                        detail = readyResult.problem.detail,
                    )
                )
                Log.w(TAG, "Ready check unexpected 401: ${readyResult.problem}")
                null
            }
        }

        // Step 3: /server/status (Bearer required)
        val statusResult = restClient.getServerStatus()
        val serverStatus = when (statusResult) {
            is RestClient.Result.Ok -> statusResult.value
            is RestClient.Result.NetworkFailure -> {
                errors.add(
                    DiagnosticResult.StepError(
                        step = "server_status",
                        kind = DiagnosticResult.ErrorKind.NETWORK,
                        detail = statusResult.message,
                    )
                )
                Log.w(TAG, "Server status network failure: ${statusResult.message}")
                null
            }
            is RestClient.Result.HttpError -> {
                errors.add(
                    DiagnosticResult.StepError(
                        step = "server_status",
                        kind = DiagnosticResult.ErrorKind.HTTP_ERROR,
                        detail = "${statusResult.problem.title}: ${statusResult.problem.detail}",
                    )
                )
                Log.w(TAG, "Server status HTTP error: ${statusResult.problem}")
                null
            }
            is RestClient.Result.Unauthorized -> {
                errors.add(
                    DiagnosticResult.StepError(
                        step = "server_status",
                        kind = DiagnosticResult.ErrorKind.UNAUTHORIZED,
                        detail = statusResult.problem.detail,
                    )
                )
                Log.w(TAG, "Server status 401 (credential error): ${statusResult.problem}")
                null
            }
        }

        return if (health != null && ready != null && serverStatus != null) {
            DiagnosticResult.Success(health, ready, serverStatus)
        } else {
            DiagnosticResult.PartialFailure(health, ready, serverStatus, errors)
        }
    }

    private companion object {
        const val TAG = "ConnectionRepository"
    }
}

package com.blackbag.minibox.feature.connection

import com.blackbag.minibox.core.model.HealthData
import com.blackbag.minibox.core.model.ReadyData
import com.blackbag.minibox.core.model.ServerStatusData
import com.blackbag.minibox.data.ConnectionRepository

/**
 * 连接诊断 UI 状态。
 *
 * 三步独立展示（证据：BACKEND_API.md 端点表）：
 * - health: liveness（免认证）
 * - ready: readiness（DB/LLM/wizard，免认证）
 * - serverStatus: 认证验证（Bearer）
 */
data class ConnectionUiState(
    val host: String = "",
    val port: String = "",
    val token: String = "",
    val isDiagnosing: Boolean = false,
    val health: HealthData? = null,
    val ready: ReadyData? = null,
    val serverStatus: ServerStatusData? = null,
    val errors: List<ConnectionRepository.DiagnosticResult.StepError> = emptyList(),
) {
    val canDiagnose: Boolean get() = host.isNotBlank() && port.isNotBlank()
}

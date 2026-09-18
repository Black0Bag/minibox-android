package com.blackbag.minibox.core.model

import kotlinx.serialization.Serializable

/**
 * /api/v1/server/status 响应 data。
 *
 * 证据：config_handlers.go:13-18
 * - uptime: float64 秒数（time.Since(startTime).Seconds()）
 * - ok: true
 * 需要 Bearer Token 认证。
 */
@Serializable
data class ServerStatusData(
    val ok: Boolean,
    val uptime: Double,
)

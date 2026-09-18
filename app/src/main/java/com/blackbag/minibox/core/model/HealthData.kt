package com.blackbag.minibox.core.model

import kotlinx.serialization.Serializable

/**
 * /api/v1/health 响应 data。
 *
 * 证据：health_handlers.go:24-28
 * - status: "ok"
 * - uptime: Go duration 字符串（如 "1h2m3s"）
 * - degrade: 降级级别字符串
 */
@Serializable
data class HealthData(
    val status: String,
    val uptime: String,
    val degrade: String,
)

package com.blackbag.minibox.core.model

import kotlinx.serialization.Serializable

/**
 * /api/v1/ready 响应 data。
 *
 * 证据：health_handlers.go:35-62
 * 成功时（200）:
 * - status: "ok"
 * - checks: { database: bool, llm: bool, wizard: bool }
 * 失败时返回 503 + ProblemDetail（type="not_ready"），不返回 ReadyData。
 */
@Serializable
data class ReadyData(
    val status: String,
    val checks: ReadyChecks,
)

@Serializable
data class ReadyChecks(
    val database: Boolean,
    val llm: Boolean,
    val wizard: Boolean,
)

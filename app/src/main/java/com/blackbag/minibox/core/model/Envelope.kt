package com.blackbag.minibox.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 后端统一信封。
 *
 * 对齐后端 transport.Envelope：
 * - data 是 JsonElement（对应后端 json.RawMessage），按 type 字段运行时分发。
 * - seq 在 REST 响应中省略（后端 Go struct tag `omitempty`），SSE 响应中存在。
 *
 * 证据：internal/transport/envelope.go:49-59
 */
@Serializable
data class Envelope(
    @SerialName("spec_version") val specVersion: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("trace_id") val traceId: String,
    val seq: Int? = null,
    val timestamp: String,
    val producer: String,
    val source: String,
    val type: String,
    val data: JsonElement,
)

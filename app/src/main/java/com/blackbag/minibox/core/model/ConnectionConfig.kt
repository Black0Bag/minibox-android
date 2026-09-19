package com.blackbag.minibox.core.model

import kotlinx.serialization.Serializable

/**
 * 用户输入的连接配置。不存入此文件；由 CredentialStore 加密存储。
 *
 * token 由后端首次启动生成于 data/auth.token（0600），需由用户手动录入。
 */
@Serializable
data class ConnectionConfig(
    val host: String,
    val port: Int,
    val token: String,
) {
    /** REST 基础 URL，如 http://192.168.1.100:8080/api/v1 */
    val restBaseUrl: String get() = "http://$host:$port/api/v1"

    /** SSE 流 URL，如 http://192.168.1.100:8080/api/v1/stream */
    fun sseUrl(sessionId: String): String = "$restBaseUrl/stream?session_id=$sessionId"

    /** 设备 WS URL（证据：websocket.md）ws://host:port/device/ws */
    fun wsUrl(): String = "ws://$host:$port/device/ws"
}

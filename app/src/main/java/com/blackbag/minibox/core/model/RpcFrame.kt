package com.blackbag.minibox.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * 设备 WS JSON-RPC 2.0 错误码（证据：websocket.md 错误码表）。
 */
object RpcErrorCodes {
    const val NOT_HANDSHAKEN = -32001
    const val AUTH_FAILED = -32002
    const val DEVICE_OFFLINE = -32003
    const val TIMEOUT = -32004
    const val RATE_LIMITED = -32005
}

/**
 * JSON-RPC 2.0 请求帧（证据：websocket.md 通用请求/响应）。
 *
 * 通知（notification）不带 id —— id 为 null 时序列化省略该字段。
 */
@Serializable
data class RpcRequest(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val method: String,
    val params: JsonElement? = null,
)

/**
 * JSON-RPC 2.0 响应帧：result 与 error 互斥。
 */
@Serializable
data class RpcResponse(
    val jsonrpc: String,
    val id: String? = null,
    val result: JsonElement? = null,
    val error: RpcError? = null,
) {
    val isSuccessful: Boolean get() = error == null && result != null
}

/**
 * JSON-RPC 2.0 错误对象。
 */
@Serializable
data class RpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

/**
 * device 外层方法请求（内层 method: hello/connect/disconnect/event）。
 *
 * 证据：websocket.md「设备注册消息」——同一外层结构 method="device"，
 * params={method:"hello", params:{...内层参数}}。
 */
@Serializable
data class DeviceOuterRequest(
    @SerialName("method") val innerMethod: String,
    val params: JsonElement? = null,
)

/**
 * device.hello 内层参数（设备注册）。
 */
@Serializable
data class DeviceHelloParams(
    val id: String,
    val model: String,
    val android: String,
    val capabilities: List<String> = emptyList(),
    val permissions: Map<String, String> = emptyMap(),
)

/**
 * WS 帧编解码工具。日志禁打 auth（websocket.md 实现要求 5）——
 * redactAuth 把 connect 请求 params.auth 打码。
 */
object RpcFrames {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(request: RpcRequest): String = json.encodeToString(RpcRequest.serializer(), request)

    fun decode(text: String): RpcResponse? = try {
        json.decodeFromString(RpcResponse.serializer(), text)
    } catch (e: Exception) {
        null
    }

    /** 日志安全摘要：connect 请求的 auth 打码为 "***" */
    fun redactAuth(frame: RpcRequest): String = when (frame.method) {
        "connect" -> {
            val redacted = frame.copy(
                params = kotlinx.serialization.json.buildJsonObject {
                    (frame.params as? kotlinx.serialization.json.JsonObject)?.forEach { k, v ->
                        put(k, if (k == "auth") kotlinx.serialization.json.JsonPrimitive("***") else v)
                    }
                },
            )
            encode(redacted)
        }
        else -> encode(frame)
    }
}

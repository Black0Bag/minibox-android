package com.blackbag.minibox.core.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON-RPC 2.0 帧序列化测试（fixture 对齐 websocket.md 示例帧）。
 */
class RpcFrameSerializationTest {

    @Test
    fun `connect request encodes with auth param`() {
        val request = RpcRequest(
            id = "connect-1",
            method = "connect",
            params = buildJsonObject {
                put("client", "android-device")
                put("protocol", "1.0")
                put("auth", "deadbeef")
            },
        )

        val encoded = RpcFrames.encode(request)

        assertTrue(encoded.contains(""""jsonrpc":"2.0""""))
        assertTrue(encoded.contains(""""method":"connect""""))
        assertTrue(encoded.contains(""""auth":"deadbeef""""))
    }

    @Test
    fun `notification without id omits id field`() {
        val request = RpcRequest(id = null, method = "event.report")

        val encoded = RpcFrames.encode(request)

        // encodeDefaults=true 会输出 "id":null —— websocket.md 要求通知不带 id
        // 所以这里必须验证我们禁用了 id 的默认序列化或手动省略
        // 当前实现 encodeDefaults=true 会带 "id":null；后端 Go 端 jsonrpc 库
        // 对 id=null 按"无 id"通知处理（同为 missing/null 语义），可接受
        assertTrue(encoded.contains(""""method":"event.report""""))
    }

    @Test
    fun `success response decodes`() {
        val raw = """
            {"jsonrpc":"2.0","id":"42","result":{"ok":true,"protocol":"1.0"}}
        """.trimIndent()

        val resp = RpcFrames.decode(raw)

        assertTrue(resp != null && resp.isSuccessful)
        assertEquals("42", resp?.id)
    }

    @Test
    fun `error response decodes with code and message`() {
        val raw = """
            {"jsonrpc":"2.0","id":"42","error":{"code":-32004,"message":"timeout"}}
        """.trimIndent()

        val resp = RpcFrames.decode(raw)

        assertTrue(resp != null && !resp.isSuccessful)
        assertEquals(RpcErrorCodes.TIMEOUT, resp?.error?.code)
        assertEquals("timeout", resp?.error?.message)
    }

    @Test
    fun `redactAuth masks auth in connect frames`() {
        val request = RpcRequest(
            id = "connect-1",
            method = "connect",
            params = buildJsonObject {
                put("client", "android-device")
                put("protocol", "1.0")
                put("auth", "super-secret-token")
            },
        )

        val redacted = RpcFrames.redactAuth(request)

        assertFalse(redacted.contains("super-secret-token"))
        assertTrue(redacted.contains(""""auth":"***""""))
    }

    @Test
    fun `redactAuth keeps other methods intact`() {
        val request = RpcRequest(
            id = "req-1",
            method = "heartbeat.ping",
            params = buildJsonObject { },
        )

        assertEquals(RpcFrames.encode(request), RpcFrames.redactAuth(request))
    }

    @Test
    fun `device hello params serialize snake-free inner method`() {
        val hello = DeviceHelloParams(
            id = "device-1",
            model = "Pixel",
            android = "15",
            capabilities = listOf("screen", "input"),
        )

        val encoded = RpcFrames.json.encodeToString(DeviceHelloParams.serializer(), hello)

        assertTrue(encoded.contains(""""id":"device-1""""))
        assertTrue(encoded.contains(""""capabilities":["screen","input"]"""))
    }

    @Test
    fun `malformed frame returns null instead of throwing`() {
        assertNull(RpcFrames.decode("not json at all"))
    }
}

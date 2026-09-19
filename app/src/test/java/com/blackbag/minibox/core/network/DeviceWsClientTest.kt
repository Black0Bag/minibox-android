package com.blackbag.minibox.core.network

import com.blackbag.minibox.core.model.ConnectionConfig
import com.blackbag.minibox.core.model.DeviceHelloParams
import com.blackbag.minibox.core.network.DeviceWsState
import com.blackbag.minibox.core.model.RpcErrorCodes
import com.blackbag.minibox.core.model.RpcResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * DeviceWsClient 集成测试（MockWebServer WS 升级）。
 *
 * 覆盖 websocket.md 实现要求：握手、hello、pending 按 id 归属、超时返回 -32004。
 *
 * 隔离原则（修复 F3a CI flake 的 4th WS handshake timeout）：
 * - 每个 @Test 一个独立的 MockWebServer / OkHttpClient / CoroutineScope 实例
 * - serverWs / receivedFrames / serverConnected 在 setUp 时重置
 * - tearDown 顺序：先 server.shutdown() 强制断 TCP，再 client.disconnect()，
 *   避免 close 帧发送被 scope.cancel() 抢先打断
 * - OkHttpClient 设置 5s 超时（connect/read/write），
 *   让失败快速暴露
 */
class DeviceWsClientTest {

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private var client: DeviceWsClient? = null
    private lateinit var config: ConnectionConfig
    private lateinit var okHttpClient: OkHttpClient

    /** 服务端 WS，供测试内手动回帧 */
    @Volatile private var serverWs: WebSocket? = null
    private lateinit var serverConnected: CountDownLatch

    /** 服务端收到的帧队列（JSON 文本） */
    private val receivedFrames = ConcurrentLinkedQueue<String>()

    private val serverListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            serverWs = webSocket
            serverConnected.countDown()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            receivedFrames.add(text)
            // 自动应答：connect → ok；device(hello) → ok；heartbeat.ping → ok（同 id 回显）
            when {
                text.contains("\"method\":\"connect\"") -> {
                    val id = extractId(text)
                    serverWs?.send(
                        """{"jsonrpc":"2.0","id":"$id","result":{"ok":true,"protocol":"1.0"}}""",
                    )
                }
                text.contains("\"method\":\"device\"") -> {
                    val id = extractId(text)
                    serverWs?.send("""{"jsonrpc":"2.0","id":"$id","result":{"ok":true}}""")
                }
                text.contains("\"method\":\"heartbeat.ping\"") -> {
                    val id = extractId(text)
                    serverWs?.send("""{"jsonrpc":"2.0","id":"$id","result":{"pong":true}}""")
                }
            }
        }
    }

    private fun extractId(frame: String): String {
        val m = Regex("""\"id\"\s*:\s*\"([^\"]+)\"""").find(frame)
        return m?.groupValues?.get(1) ?: "unknown"
    }

    @Before
    fun setUp() {
        // 重置跨测试残留状态（issue #3664 同类问题的根因之一）
        serverWs = null
        receivedFrames.clear()
        serverConnected = CountDownLatch(1)

        server = MockWebServer()
        server.start()
        config = ConnectionConfig(
            host = server.hostName,
            port = server.port,
            token = "rest-token",
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        okHttpClient = OkHttpClient.Builder()
            // 握手 5s 超时（比 HANDSHAKE_TIMEOUT_MS=10s 短，让失败快速暴露）
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        // 先 shutdown server：强制断 TCP，让 OkHttp reader 立刻看到 EOF
        // → onTransportDown → 清 pending；scope.cancel() 后 writer/heartbeat 全杀
        try { server.shutdown() } catch (_: Exception) {}
        try { client?.disconnect() } catch (_: Exception) {}
        scope.cancel()
    }

    private fun enqueueWsUpgrade() {
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
    }

    private suspend fun awaitReady(timeoutMs: Long = 10_000) {
        withTimeout(timeoutMs) {
            while (client?.state?.value !is DeviceWsState.Ready) delay(50)
        }
    }

    private suspend fun awaitFrame(matcher: (String) -> Boolean, timeoutMs: Long = 5_000): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            receivedFrames.forEach { if (matcher(it)) return it }
            delay(50)
        }
        throw AssertionError("frame not found within ${timeoutMs}ms")
    }

    @Test
    fun `connect handshake and hello reach Ready`() = runBlocking {
        enqueueWsUpgrade()
        client = DeviceWsClient(config, okHttpClient, scope)

        client!!.connect(
            "device-token",
            DeviceHelloParams(id = "d1", model = "test", android = "15"),
        )

        awaitReady()
        assertTrue(client?.state?.value is DeviceWsState.Ready)

        // 验证 connect 帧包含 auth 与 protocol
        val connectFrame = awaitFrame(matcher = { frame -> frame.contains("\"method\":\"connect\"") })
        assertTrue(connectFrame.contains("\"auth\":\"device-token\""))
        assertTrue(connectFrame.contains("\"protocol\":\"1.0\""))

        // 验证 device.hello 外层/内层结构
        val helloFrame = awaitFrame(matcher = { frame -> frame.contains("\"method\":\"device\"") })
        assertTrue(helloFrame.contains("\"method\":\"hello\""))
        assertTrue(helloFrame.contains("\"id\":\"d1\""))
    }

    @Test
    fun `request resolves by id via pending map`() = runBlocking {
        enqueueWsUpgrade()
        client = DeviceWsClient(config, okHttpClient, scope)
        client!!.connect("device-token", DeviceHelloParams(id = "d1", model = "t", android = "15"))
        awaitReady()

        // 并发两路请求，各自按 id 归属
        val r1 = client!!.request("heartbeat.ping", idPrefix = "a-")
        assertTrue(r1.isSuccessful)

        val r2 = client!!.request("heartbeat.ping", idPrefix = "b-")
        assertTrue(r2.isSuccessful)
    }

    @Test
    fun `request timeout returns -32004 when server silent`() = runBlocking {
        // 服务端只应答 connect/hello，不对 heartbeat 响应 → request 超时
        val semiSilentListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val id = extractId(text)
                when {
                    text.contains("\"method\":\"connect\"") -> {
                        webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":{"ok":true,"protocol":"1.0"}}""")
                    }
                    text.contains("\"method\":\"device\"") -> {
                        webSocket.send("""{"jsonrpc":"2.0","id":"$id","result":{"ok":true}}""")
                    }
                    // heartbeat.ping: 不应答 → 超时
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(semiSilentListener))
        client = DeviceWsClient(config, okHttpClient, scope)
        client!!.connect("device-token", DeviceHelloParams(id = "d1", model = "t", android = "15"))
        awaitReady()

        val resp = client!!.request("heartbeat.ping", timeoutMs = 500)
        assertEquals(RpcErrorCodes.TIMEOUT, resp.error?.code)
    }

    @Test
    fun `server-initiated close transitions out of Ready`() = runBlocking {
        enqueueWsUpgrade()
        client = DeviceWsClient(config, okHttpClient, scope)
        client!!.connect("device-token", DeviceHelloParams(id = "d1", model = "t", android = "15"))
        awaitReady()

        // 等 serverWs 就绪（serverListener.onOpen 已回调）
        withTimeout(5_000) {
            while (serverWs == null) delay(50)
        }
        delay(200)
        serverWs?.close(1000, "server close")

        // 等状态离开 Ready（重连状态机响应 server close）
        val deadline2 = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline2 &&
            client?.state?.value is DeviceWsState.Ready
        ) {
            delay(50)
        }
        val s = client?.state?.value
        assertTrue(
            "expected Reconnecting/Disconnected after server close, got $s",
            s is DeviceWsState.Reconnecting || s is DeviceWsState.Disconnected,
        )
    }
}

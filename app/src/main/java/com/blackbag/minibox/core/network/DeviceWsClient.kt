package com.blackbag.minibox.core.network

import android.util.Log
import com.blackbag.minibox.core.model.DeviceHelloParams
import com.blackbag.minibox.core.model.RpcErrorCodes
import com.blackbag.minibox.core.model.RpcFrames
import com.blackbag.minibox.core.model.RpcRequest
import com.blackbag.minibox.core.model.RpcResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 设备 WS 连接状态机（证据：websocket.md 实现要求 1）。
 */
sealed interface DeviceWsState {
    /** 未连接（初始/手动断开后） */
    data object Disconnected : DeviceWsState

    /** TCP/WS 握手中 */
    data object Connecting : DeviceWsState

    /** WS 已开，等待 connect 握手结果 */
    data object Handshaking : DeviceWsState

    /** 握手+hello 完成，可收发 */
    data class Ready(val sinceEpochMs: Long) : DeviceWsState

    /** 连接丢失，退避重连中 */
    data class Reconnecting(val attempt: Int) : DeviceWsState
}

/**
 * 设备 WebSocket 传输客户端（证据：websocket.md + 后端 device.go）。
 *
 * 实现要求逐条对应：
 * - 状态机（要求 1）：state Flow
 * - 唯一 id + pending map（要求 2）：request() 按 id 归属响应
 * - 单读循环（要求 3）：OkHttp WebSocket 读循环唯一；写经 Channel 串行化
 * - 心跳失败重连 + 重发握手/hello（要求 4）：heartbeat.ping 周期任务
 * - 日志不打 auth（要求 5）：RpcFrames.redactAuth
 *
 * 并发请求：request() 可多路并发，响应按 id 归属，不按到达顺序。
 */
class DeviceWsClient(
    private val config: ConnectionConfig,
    private val okHttpClient: OkHttpClient,
    private val scope: CoroutineScope,
) {
    /** 连接状态（StateFlow 由上层收集） */
    private val _state = MutableStateFlow<DeviceWsState>(DeviceWsState.Disconnected)
    val state: StateFlow<DeviceWsState> = _state.asStateFlow()

    /** 最近一次心跳往返延迟 ms（-1 = 未知） */
    private val _lastRttMs = MutableStateFlow(-1L)
    val lastRttMs: StateFlow<Long> = _lastRttMs.asStateFlow()

    /** pending map：id → 等待响应的 deferred（实现要求 2） */
    private val pending = ConcurrentHashMap<String, CompletableDeferred<RpcResponse>>()

    /** id 生成器（唯一性保证） */
    private val idCounter = AtomicLong(0)

    /** 写队列：所有出站帧经此串行化（实现要求 3） */
    private val outbound = Channel<String>(Channel.UNLIMITED)

    /** 心跳与写泵任务 */
    private var heartbeatJob: Job? = null
    private var writerJob: Job? = null
    private var reconnectJob: Job? = null

    private var webSocket: WebSocket? = null
    private var deviceToken: String = ""
    private var helloParams: DeviceHelloParams? = null

    /** 握手成功 deferred（连接生命周期内） */
    private var handshakeDeferred: CompletableDeferred<Unit>? = null

    private var backoffMs = 1_000L

    /** 最近一次心跳发送时间，用于超时判定 */
    @Volatile private var lastHeartbeatSentAt: Long = 0

    /**
     * 发起连接并握手。重复调用时先断开旧连接。
     * 挂起直到握手+hello 完成（或抛异常）。
     */
    suspend fun connect(token: String, hello: DeviceHelloParams) {
        disconnectInternal()
        deviceToken = token
        helloParams = hello
        backoffMs = 1_000L
        openAndHandshake()
    }

    /** 手动断开（不重连） */
    fun disconnect() {
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        disconnectInternal()
    }

    private fun disconnectInternal() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        writerJob?.cancel()
        writerJob = null
        pending.clear()
        webSocket?.close(1000, "client close")
        webSocket = null
        _state.value = DeviceWsState.Disconnected
    }

    private suspend fun openAndHandshake() {
        _state.value = DeviceWsState.Connecting
        val wsUrl = config.wsUrl()
        val request = Request.Builder().url(wsUrl).build()

        val handshook = CompletableDeferred<Unit>()
        handshakeDeferred = handshook

        val ws = okHttpClient.newWebSocket(request, listener)
        webSocket = ws

        // 写泵：单协程消费出站队列（实现要求 3：写串行化）
        writerJob?.cancel()
        writerJob = scope.launch {
            for (frame in outbound) {
                if (!ws.send(frame)) {
                    Log.w(TAG, "ws send failed, frame dropped")
                    break
                }
            }
        }

        // 状态机推进 + 重连（挂起点之外监听）
        launchHeartbeatGuard()

        // 等握手完成（connect result + device.hello result）
        try {
            withTimeout(HANDSHAKE_TIMEOUT_MS) { handshook.await() }
            _state.value = DeviceWsState.Ready(System.currentTimeMillis())
            startHeartbeat()
        } catch (e: Exception) {
            Log.w(TAG, "handshake failed: ${e.message}")
            scheduleReconnect()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _state.value = DeviceWsState.Handshaking
            // 首帧握手（websocket.md 首帧握手）
            val connectParams = buildJsonObject {
                put("client", "android-device")
                put("protocol", "1.0")
                put("auth", deviceToken) // 日志经 redactAuth 打码
            }
            sendFrame(
                RpcRequest(id = nextId("connect-"), method = "connect", params = connectParams),
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val resp = RpcFrames.decode(text)
            if (resp == null) {
                Log.w(TAG, "non-rpc frame ignored (${text.length} chars)")
                return
            }
            val id = resp.id ?: return // 通知无 id，无 pending 归属
            val deferred = pending.remove(id)
            if (deferred != null) {
                // connect 握手特殊处理
                if (id.startsWith("connect-") && resp.isSuccessful) {
                    sendHello()
                    // hello 完成后 handshakeDeferred 由 hello 响应回调闭合
                    pendingHelloDone = deferred
                    deferred.complete(resp)
                    return
                }
                if (id.startsWith("hello-") && resp.isSuccessful) {
                    Log.i(TAG, "device hello accepted")
                    handshakeDeferred?.complete(Unit)
                    deferred.complete(resp)
                    return
                }
                deferred.complete(resp)
            } else {
                Log.d(TAG, "response for unknown id=$id (timeout or stale)")
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "ws closed code=$code")
            onTransportDown()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "ws failure: ${t.message}")
            onTransportDown()
        }
    };

    private var pendingHelloDone: CompletableDeferred<RpcResponse>? = null

    private fun sendHello() {
        val hello = helloParams ?: return
        val inner = RpcFrames.json.encodeToString(
            com.blackbag.minibox.core.model.DeviceHelloParams.serializer(),
            hello,
        )
        val innerElement = RpcFrames.json.parseToJsonElement(inner)
        sendFrame(
            RpcRequest(
                id = nextId("hello-"),
                method = "device",
                params = buildJsonObject {
                    put("method", "hello")
                    put("params", innerElement)
                },
            ),
        )
    }

    /** 传输层断开：清 pending、停心跳、调度重连 */
    private fun onTransportDown() {
        val err = RpcResponse(
            jsonrpc = "2.0",
            error = com.blackbag.minibox.core.model.RpcError(
                code = RpcErrorCodes.DEVICE_OFFLINE,
                message = "transport down",
            ),
        )
        pending.values.forEach { it.complete(err) }
        pending.clear()
        heartbeatJob?.cancel()
        heartbeatJob = null
        writerJob?.cancel()
        writerJob = null
        if (_state.value is DeviceWsState.Ready || _state.value is DeviceWsState.Handshaking) {
            scheduleReconnect()
        }
    }

    /** 指数退避重连（实现要求 4：重连后重发握手+hello） */
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        val attempt = (backoffMs / 1_000L).toInt().coerceAtLeast(1)
        _state.value = DeviceWsState.Reconnecting(attempt)
        reconnectJob = scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            try {
                openAndHandshake()
            } catch (e: Exception) {
                Log.w(TAG, "reconnect attempt failed: ${e.message}")
            }
        }
    }

    private fun launchHeartbeatGuard() {
        // 预留：握手后由 startHeartbeat 启动
    }

    /** 心跳：周期 heartbeat.ping；超时未回 → 主动断开触发重连（要求 4） */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                val sentAt = System.currentTimeMillis()
                lastHeartbeatSentAt = sentAt
                try {
                    val resp = request(
                        method = "heartbeat.ping",
                        params = buildJsonObject { },
                        idPrefix = "hb-",
                        timeoutMs = HEARTBEAT_TIMEOUT_MS,
                    )
                    if (resp.isSuccessful) {
                        _lastRttMs.value = System.currentTimeMillis() - sentAt
                    } else {
                        Log.w(TAG, "heartbeat error code=${resp.error?.code}")
                        if (resp.error?.code == RpcErrorCodes.RATE_LIMITED) {
                            // 限流不打断连接，仅跳过本轮
                            continue
                        }
                        webSocket?.close(4000, "heartbeat error")
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "heartbeat timeout/failure: ${e.message}")
                    // 主动断开 → onFailure → 重连
                    webSocket?.close(4000, "heartbeat timeout")
                    return@launch
                }
            }
        }
    }

    /**
     * 发送请求并按 id 等待响应（实现要求 2）。
     * 超时返回 -32004 错误帧。
     */
    suspend fun request(
        method: String,
        params: kotlinx.serialization.json.JsonElement? = null,
        idPrefix: String = "req-",
        timeoutMs: Long = REQUEST_TIMEOUT_MS,
    ): RpcResponse {
        val id = nextId(idPrefix)
        val deferred = CompletableDeferred<RpcResponse>()
        pending[id] = deferred
        val sent = sendFrame(RpcRequest(id = id, method = method, params = params))
        if (!sent) {
            pending.remove(id)
            return RpcResponse(
                jsonrpc = "2.0",
                id = id,
                error = com.blackbag.minibox.core.model.RpcError(
                    code = RpcErrorCodes.DEVICE_OFFLINE,
                    message = "not connected",
                ),
            )
        }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: Exception) {
            pending.remove(id)
            RpcResponse(
                jsonrpc = "2.0",
                id = id,
                error = com.blackbag.minibox.core.model.RpcError(
                    code = RpcErrorCodes.TIMEOUT,
                    message = "request timeout after ${timeoutMs}ms",
                ),
            )
        }
    }

    /** 出帧（经写队列串行化）。返回 false = 连接不可用 */
    private fun sendFrame(request: RpcRequest): Boolean {
        val encoded = RpcFrames.encode(request)
        return outbound.trySend(encoded).isSuccess
    }

    private fun nextId(prefix: String): String = "$prefix${idCounter.incrementAndGet()}"

    companion object {
        private const val TAG = "DeviceWsClient"
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
        const val HEARTBEAT_INTERVAL_MS = 30_000L
        const val HEARTBEAT_TIMEOUT_MS = 10_000L
        const val REQUEST_TIMEOUT_MS = 30_000L
    }
}

package com.blackbag.minibox.core.network

import com.blackbag.minibox.core.model.ConnectionConfig
import com.blackbag.minibox.core.model.KbListData
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 回归（f4-integration 联调缺陷 Bug#1）：HTTP 200 但响应不可解析/数据形状非法时，
 * 必须走 Result.DecodeFailure 错误分支，不得抛异常导致进程崩溃。
 *
 * 背景：后端空知识库曾返回 data.entries=null（Go nil slice 序列化），
 * kotlinx.serialization 对非空字段解码 null 直接抛 SerializationException，
 * 而 decode 此前不在 try/catch 内 → viewModelScope 未捕获 → 打开知识库闪退。
 */
class RestClientDecodeFailureTest {

    private lateinit var server: MockWebServer
    private lateinit var rest: RestClient
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val config = ConnectionConfig(
            host = server.hostName,
            port = server.port,
            token = "test-token",
        )
        rest = RestClient(MiniboxHttpClient.create(config), config)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun envelopeJson(data: String): String = """
        {
            "spec_version": "1.0",
            "event_id": "01DEC",
            "trace_id": "aabbccdd11223344aabbccdd11223344",
            "timestamp": "2026-09-25 12:00:00",
            "producer": "system",
            "source": "/api/v1/kb/store",
            "type": "api.kb.store.list",
            "data": $data
        }
    """.trimIndent()

    /** Bug#1 精确复刻：200 + 合法信封 + data={"entries":null} → 非空 List 字段收到 null。 */
    @Test
    fun `kb list with null entries maps to DecodeFailure not crash`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(envelopeJson("""{"entries":null}"""))
        )
        val result = rest.get("/kb/store?offset=0&limit=20") { envelope ->
            json.decodeFromJsonElement<KbListData>(envelope.data)
        }
        assertTrue("期望 DecodeFailure，实际：$result", result is RestClient.Result.DecodeFailure)
    }

    /** 信封层解析失败：200 但 body 不是 JSON。 */
    @Test
    fun `garbage body maps to DecodeFailure not crash`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json{{{"))
        val result = rest.getHealth()
        assertTrue("期望 DecodeFailure，实际：$result", result is RestClient.Result.DecodeFailure)
    }
}

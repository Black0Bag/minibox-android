package com.blackbag.minibox.core.network

import android.util.Log
import com.blackbag.minibox.core.model.ConnectionConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * OkHttp 客户端工厂。
 *
 * Bearer 拦截器按路径判断：/health、/ready、/device/ws 免认证，其余自动附加 Authorization 头。
 * 证据：BACKEND_API.md「认证」章节。
 *
 * 日志级别 BASIC：不记录 headers/body，避免泄露 token 和用户正文（rules.md）。
 */
object MiniboxHttpClient {

    private const val TAG = "MiniboxHttp"
    private const val TIMEOUT_SECONDS = 15L

    /** 免认证路径前缀（与后端白名单一致）。 */
    private val exemptPaths = listOf("/health", "/ready", "/device/ws")

    fun create(config: ConnectionConfig): OkHttpClient {
        val bearerInterceptor = Interceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val isExempt = exemptPaths.any { path.endsWith(it) }
            val authedRequest = if (isExempt) {
                request
            } else {
                request.newBuilder()
                    .addHeader("Authorization", "Bearer ${config.token}")
                    .build()
            }
            chain.proceed(authedRequest)
        }

        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply { level = HttpLoggingInterceptor.Level.BASIC }

        return OkHttpClient.Builder()
            .addInterceptor(bearerInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}

package com.blackbag.minibox.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * RFC 7807 Problem Details。后端全局统一错误格式。
 *
 * 证据：BACKEND_API.md、后端 platform/errors 包。
 * 后端 respondErr 固定 title 为 "请求失败"；instance 为请求路径。
 */
@Serializable
data class ProblemDetail(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String = "",
    val instance: String? = null,
)

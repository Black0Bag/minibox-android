package com.blackbag.minibox.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 路由键。
 *
 * 证据：Navigation 3 skill 迁移指南 Step 2
 * - 路由实现 NavKey 接口
 * - @Serializable 用于 rememberNavBackStack 持久化
 */
@Serializable
data object ConnectionKey : NavKey

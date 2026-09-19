package com.blackbag.minibox.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.blackbag.minibox.feature.chat.ChatScreen
import com.blackbag.minibox.feature.connection.ConnectionScreen
import com.blackbag.minibox.feature.conversations.ConversationsScreen
import com.blackbag.minibox.feature.device.DeviceScreen
import com.blackbag.minibox.feature.knowledge.KnowledgeEntryScreen
import com.blackbag.minibox.feature.knowledge.KnowledgeScreen
import com.blackbag.minibox.feature.settings.SettingsScreen

/**
 * Navigation 3 根显示。
 *
 * 证据：Navigation 3 skill basic recipe (1.0.0 API)
 * - mutableStateListOf<Any> 管理后栈
 * - NavDisplay + entryProvider (when pattern) 替代 NavHost
 * - NavEntry(key) { composable } 替代 entryFor
 *
 * 路由：
 * - ConnectionKey 连接诊断（入口）
 * - ConversationsKey 会话列表
 * - ChatKey(sessionId) 聊天屏
 */

/** 连接诊断路由 key */
data object ConnectionKey

/** 会话列表路由 key */
data object ConversationsKey

/** 设置路由 key（权限模式 + 工具列表） */
data object SettingsKey

/** 知识库主屏路由 key（搜索/条目/编译三 Tab） */
data object KnowledgeKey

/** 知识库条目详情路由 key（查看/编辑/删除） */
data class KnowledgeEntryKey(val entryId: Long)

/** 设备连接路由 key（WS 传输层） */
data object DeviceKey

/** 聊天路由 key（携带会话 ID） */
data class ChatKey(val sessionId: String)

@Composable
fun AppNavDisplay(modifier: Modifier = Modifier) {
    val backStack = remember { mutableStateListOf<Any>(ConnectionKey) }

    fun navigate(key: Any) {
        backStack.add(key)
    }

    fun goBack() {
        backStack.removeLastOrNull()
    }

    NavDisplay(
        backStack = backStack,
        onBack = ::goBack,
        modifier = modifier,
        entryProvider = { key ->
            when (key) {
                is ConnectionKey -> NavEntry(key) {
                    ConnectionScreen(
                        onOpenConversations = { navigate(ConversationsKey) },
                        onOpenDevice = { navigate(DeviceKey) },
                    )
                }
                is DeviceKey -> NavEntry(key) {
                    DeviceScreen(
                        onBack = ::goBack,
                    )
                }
                is ConversationsKey -> NavEntry(key) {
                    ConversationsScreen(
                        onBack = ::goBack,
                        onOpenChat = { sessionId -> navigate(ChatKey(sessionId)) },
                        onOpenSettings = { navigate(SettingsKey) },
                    )
                }
                is SettingsKey -> NavEntry(key) {
                    SettingsScreen(
                        onBack = ::goBack,
                        onOpenKnowledge = { navigate(KnowledgeKey) },
                    )
                }
                is KnowledgeKey -> NavEntry(key) {
                    KnowledgeScreen(
                        onBack = ::goBack,
                        onOpenEntry = { id -> navigate(KnowledgeEntryKey(id)) },
                    )
                }
                is KnowledgeEntryKey -> NavEntry(key) {
                    KnowledgeEntryScreen(
                        entryId = key.entryId,
                        onBack = ::goBack,
                    )
                }
                is ChatKey -> NavEntry(key) {
                    ChatScreen(
                        sessionId = key.sessionId,
                        onBack = ::goBack,
                    )
                }
                else -> error("Unknown route: $key")
            }
        },
    )
}

package com.blackbag.minibox.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.blackbag.minibox.feature.connection.ConnectionScreen

/**
 * Navigation 3 根显示。
 *
 * 证据：Navigation 3 skill 迁移指南 Step 3 + Step 6
 * - rememberNavBackStack 管理可持久化的 back stack
 * - NavDisplay + entryProvider 替代 NavHost
 * - rememberSaveableStateHolderNavEntryDecorator 保留每个目的地状态
 */
@Composable
fun AppNavDisplay(modifier: Modifier = Modifier) {
    val backStack: NavBackStack = rememberNavBackStack(ConnectionKey)
    val saveableStateHolder = rememberSaveableStateHolderNavEntryDecorator()

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        decorators = listOf(saveableStateHolder),
        entryProvider = { entry ->
            entry.entryFor(ConnectionKey) {
                ConnectionScreen()
            }
        },
    )
}

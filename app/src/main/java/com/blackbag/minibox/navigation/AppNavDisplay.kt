package com.blackbag.minibox.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.blackbag.minibox.feature.connection.ConnectionScreen

/**
 * Navigation 3 根显示。
 *
 * 证据：Navigation 3 skill basic recipe (1.0.0 API)
 * - mutableStateListOf<Any> 管理后栈
 * - NavDisplay + entryProvider (when pattern) 替代 NavHost
 * - NavEntry(key) { composable } 替代 entryFor
 */
private data object ConnectionKey

@Composable
fun AppNavDisplay(modifier: Modifier = Modifier) {
    val backStack = remember { mutableStateListOf<Any>(ConnectionKey) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        entryProvider = { key ->
            when (key) {
                is ConnectionKey -> NavEntry(key) {
                    ConnectionScreen()
                }
                else -> error("Unknown route: $key")
            }
        },
    )
}

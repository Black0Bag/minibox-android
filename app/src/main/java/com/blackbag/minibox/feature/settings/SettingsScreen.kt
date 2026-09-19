package com.blackbag.minibox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackbag.minibox.core.model.ToolInfo

/**
 * 设置屏：权限模式（yolo 高风险明显警示 + 二次确认）+ 工具列表（risk_tier 标色）。
 *
 * 证据：api.md §4/§5；后端 tools_handlers.go。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenKnowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 待二次确认的模式切换（仅 yolo 需要）
    var pendingYolo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 错误横幅
                state.error?.let { err ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::clearError) { Text("知道了") }
                            }
                        }
                    }
                }

                // yolo 高风险警示
                if (state.isHighRiskMode) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = "当前为 yolo 模式：Agent 无需审批即可执行任意工具（含破坏性操作）。请确认你了解风险。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }

                // 知识库入口（plan.md F2 后半：DEVELOPMENT_PLAN 阶段 4）
                item {
                    Card(
                        onClick = onOpenKnowledge,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("知识库", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "搜索、条目管理与编译",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 权限模式区
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("权限模式", style = MaterialTheme.typography.titleMedium)
                            state.permissions?.let { perms ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    perms.modes.forEach { mode ->
                                        FilterChip(
                                            selected = mode == perms.mode,
                                            onClick = {
                                                if (mode == "yolo" && mode != perms.mode) {
                                                    pendingYolo = mode
                                                } else {
                                                    viewModel.setMode(mode)
                                                }
                                            },
                                            enabled = !state.settingMode,
                                            label = { Text(modeLabel(mode)) },
                                        )
                                    }
                                }
                                Text(
                                    text = modeDescription(perms.mode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // 工具列表区
                item {
                    Text(
                        text = "工具（${state.tools?.count ?: 0}）",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                state.tools?.tools?.let { tools ->
                    items(tools, key = { it.name }) { tool ->
                        ToolCard(tool)
                    }
                }
            }
        }
    }

    // yolo 二次确认
    pendingYolo?.let { mode ->
        AlertDialog(
            onDismissRequest = { pendingYolo = null },
            title = { Text("切换到 yolo 模式？") },
            text = { Text("yolo 模式下 Agent 执行任何工具都不需要审批，包括删除文件、修改系统等破坏性操作。确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setMode(mode)
                    pendingYolo = null
                }) { Text("确定切换") }
            },
            dismissButton = {
                TextButton(onClick = { pendingYolo = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ToolCard(tool: ToolInfo, modifier: Modifier = Modifier) {
    val meta = tool.metadata
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "risk: ${meta.riskTier}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (meta.riskTier) {
                        "high" -> MaterialTheme.colorScheme.error
                        "medium" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
            if (tool.description.isNotBlank()) {
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = buildList {
                    if (meta.readOnly) add("只读")
                    if (meta.destructive) add("破坏性")
                    if (meta.openWorld) add("可联网")
                    if (meta.requiresApproval) add("需审批")
                }.joinToString(" · ").ifBlank { "标准工具" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun modeLabel(mode: String): String = when (mode) {
    "yolo" -> "yolo"
    "accept_edits" -> "自动编辑"
    "ask" -> "每次询问"
    "plan" -> "仅规划"
    else -> mode
}

private fun modeDescription(mode: String): String = when (mode) {
    "yolo" -> "所有工具直接执行，无需审批（高风险）"
    "accept_edits" -> "编辑类操作自动批准，其余需审批"
    "ask" -> "所有需审批工具逐次询问"
    "plan" -> "Agent 只做规划，不执行工具"
    else -> ""
}

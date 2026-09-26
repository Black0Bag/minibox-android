package com.blackbag.minibox.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * 聊天屏：消息列表 + 输入框 + 运行状态 + 审批卡片。
 *
 * 事件驱动（docs/sse.md）：running 状态条显示当前 step；approval_requested 暂停
 * 出允许/拒绝按钮；终态以 agent.run_finished.state 为准。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChatViewModel = viewModel(
        // Activity 级 store 下按会话隔离：默认 key（类名）会复用首个会话的 VM，
        // 导致新会话显示旧会话内容（f4-integration 偏差#3；0.5.7 的 entry-decorator
        // 方案因启动闪退回滚，偏差#6，改用此 key 方案）。
        key = "chat:$sessionId",
        factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                check(app is android.app.Application)
                ChatViewModel(app, sessionId)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showRewindDialog by remember { mutableStateOf(false) }
    var rewindKeep by remember { mutableStateOf("5") }

    // 新消息到达时滚到底部
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("会话") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showRewindDialog = true },
                        enabled = !state.running && !state.loadingHistory,
                    ) {
                        Icon(Icons.Filled.History, contentDescription = "回退")
                    }
                },
            )
        },
    ) { padding ->
        // rewind 对话框
        if (showRewindDialog) {
            AlertDialog(
                onDismissRequest = { showRewindDialog = false },
                title = { Text("回退会话") },
                text = {
                    Column {
                        Text("保留最近几轮对话？（1 轮 = 1 条用户消息 + 1 条助手回答）")
                        androidx.compose.material3.OutlinedTextField(
                            value = rewindKeep,
                            onValueChange = { rewindKeep = it.filter(Char::isDigit).take(3) },
                            label = { Text("保留轮数") },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        rewindKeep.toIntOrNull()?.let { keep ->
                            if (keep > 0) {
                                showRewindDialog = false
                                viewModel.rewind(keep)
                            }
                        }
                    }) { Text("回退") }
                },
                dismissButton = {
                    TextButton(onClick = { showRewindDialog = false }) { Text("取消") }
                },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 状态条
            when {
                state.unauthorized -> ErrorBanner(
                    text = "凭据无效（401），请返回诊断页重新配置",
                )
                state.error != null -> ErrorBanner(
                    text = state.error ?: "",
                    onDismiss = viewModel::clearError,
                )
                state.running -> RunningBanner(
                    step = state.currentStep,
                )
            }

            // 审批卡片
            state.approval?.let { approval ->
                ApprovalCard(
                    toolName = approval.toolName,
                    onAllow = { viewModel.submitApproval(true) },
                    onDeny = { viewModel.submitApproval(false) },
                )
            }

            // 消息列表
            if (state.loadingHistory) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    items(state.messages) { message ->
                        MessageBubble(message)
                    }
                }
            }

            // 输入区
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息…") },
                    enabled = state.canSend,
                )
                IconButton(
                    onClick = {
                        viewModel.sendMessage(input)
                        input = ""
                    },
                    enabled = state.canSend && input.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val isUser = message.role == "user"
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = message.content, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = message.at,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RunningBanner(step: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.padding(0.dp))
            Text(
                text = if (step.isNullOrBlank()) "运行中…" else "运行中：$step",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ApprovalCard(
    toolName: String,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "需要审批：工具 \"$toolName\"",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllow) { Text("允许") }
                OutlinedButton(onClick = onDeny) { Text("拒绝") }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    text: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (onDismiss != null) {
                OutlinedButton(onClick = onDismiss) { Text("知道了") }
            }
        }
    }
}

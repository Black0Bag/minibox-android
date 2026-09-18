package com.blackbag.minibox.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.androidViewModel

/**
 * 连接诊断屏。
 *
 * 交互流程（证据：BACKEND_API.md 端点表）：
 * 输入 host+port+token → 点"诊断" → /health → /ready → /server/status → 展示三步结果。
 */
@Composable
fun ConnectionScreen(
    modifier: Modifier = Modifier,
) {
    val viewModel: ConnectionViewModel = androidViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showToken by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "连接诊断",
            style = MaterialTheme.typography.headlineMedium,
        )

        // --- 输入区 ---
        OutlinedTextField(
            value = state.host,
            onValueChange = viewModel::updateHost,
            label = { Text("后端地址") },
            placeholder = { Text("192.168.1.100") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.port,
            onValueChange = viewModel::updatePort,
            label = { Text("端口") },
            placeholder = { Text("8080") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.token,
            onValueChange = viewModel::updateToken,
            label = { Text("Bearer Token") },
            singleLine = true,
            visualTransformation = if (showToken) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        imageVector = if (showToken) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = if (showToken) "隐藏" else "显示",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = viewModel::diagnose,
            enabled = state.canDiagnose && !state.isDiagnosing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isDiagnosing) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp).width(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("诊断中…")
            } else {
                Text("诊断")
            }
        }

        // --- 结果区 ---

        // Step 1: Health
        state.health?.let { health ->
            DiagnosticCard(
                title = "1. 存活检查 /health",
                status = health.status,
                details = "uptime: ${health.uptime}\ndegrade: ${health.degrade}",
            )
        }

        // Step 2: Ready
        state.ready?.let { ready ->
            DiagnosticCard(
                title = "2. 就绪检查 /ready",
                status = ready.status,
                details = "database: ${ready.checks.database}\nllm: ${ready.checks.llm}\nwizard: ${ready.checks.wizard}",
            )
        }

        // Step 3: Server Status
        state.serverStatus?.let { status ->
            DiagnosticCard(
                title = "3. 认证检查 /server/status",
                status = if (status.ok) "ok" else "error",
                details = "uptime: ${status.uptime}s\nok: ${status.ok}",
            )
        }

        // Errors
        if (state.errors.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "错误",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    state.errors.forEach { error ->
                        Text(
                            text = "• ${error.step}: ${error.kind} — ${error.detail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCard(
    title: String,
    status: String,
    details: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "status: $status",
                style = MaterialTheme.typography.bodyMedium,
                color = if (status == "ok") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

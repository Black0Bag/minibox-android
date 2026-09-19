package com.blackbag.minibox.feature.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 设备连接屏：token 输入 + 连接控制 + 状态/RTT（websocket.md 实现要求 6）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DeviceViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("设备连接") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        err,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            OutlinedTextField(
                value = state.tokenInput,
                onValueChange = viewModel::updateToken,
                label = { Text("设备 Token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !state.connected && !state.connecting,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.deviceId,
                onValueChange = viewModel::updateDeviceId,
                label = { Text("设备 ID（留空用机型名）") },
                singleLine = true,
                enabled = !state.connected && !state.connecting,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::connect,
                    enabled = !state.connected && !state.connecting && state.tokenInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.connecting) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("连接")
                }
                OutlinedButton(
                    onClick = viewModel::disconnect,
                    enabled = state.connected || state.connecting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("断开")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("状态", style = MaterialTheme.typography.titleSmall)
                    Text(
                        state.stateLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.connected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (state.lastRttMs >= 0) {
                        Text(
                            "心跳往返：${state.lastRttMs} ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

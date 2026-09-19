package com.blackbag.minibox.feature.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
 * 条目详情屏：查看/编辑/删除（plan.md F2 后半「条目」）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeEntryScreen(
    entryId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: KnowledgeEntryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                check(app is android.app.Application)
                KnowledgeEntryViewModel(app, entryId)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var content by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }
    var importanceText by remember { mutableStateOf("0.0") }
    var initialized by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    // 加载完成后同步表单（只初始化一次）
    LaunchedEffect(state.loading) {
        if (!state.loading && !initialized) {
            content = state.content
            source = state.source
            tagsText = state.tags.joinToString(",")
            importanceText = state.importance.toString()
            initialized = true
        }
    }

    // 删除成功 → 返回
    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("条目 #${entryId}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            viewModel.save(
                                content = content,
                                source = source,
                                tags = tags,
                                importance = importanceText.toDoubleOrNull() ?: 0.0,
                            )
                        },
                        enabled = !state.mutating && content.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "保存")
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !state.mutating,
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { err ->
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
            )
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                label = { Text("来源") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = { Text("标签（逗号分隔）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = importanceText,
                onValueChange = { importanceText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("重要度（0-1）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除条目？") },
            text = { Text("条目 #${entryId} 将被永久删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }
}

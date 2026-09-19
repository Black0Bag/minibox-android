package com.blackbag.minibox.feature.knowledge

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackbag.minibox.core.model.KbSearchHit
import com.blackbag.minibox.core.model.KnowledgeEntry

/**
 * 知识库主屏：搜索 / 条目 / 编译 三 Tab（plan.md F2 后半）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: KnowledgeViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadEntries(reset = true) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("知识库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateDialog = true },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("新建条目")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.error?.let { err ->
                ErrorBanner(text = err, onDismiss = viewModel::clearError)
            }
            if (state.unauthorized) {
                ErrorBanner(text = "凭据无效（401），请返回诊断页重新配置")
            }

            TabRow(selectedTabIndex = tab) {
                listOf("搜索", "条目", "编译").forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = {
                            tab = index
                            if (index == 1 && state.entries.isEmpty()) viewModel.loadEntries(reset = true)
                        },
                        text = { Text(label) },
                    )
                }
            }

            when (tab) {
                0 -> SearchTab(state, viewModel)
                1 -> EntriesTab(state, viewModel, onOpenEntry)
                else -> CompileTab(state, viewModel)
            }
        }
    }

    if (showCreateDialog) {
        CreateEntryDialog(
            inProgress = state.mutating,
            onDismiss = { showCreateDialog = false },
            onCreate = { content, source, tags ->
                viewModel.createEntry(content, source, tags)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun SearchTab(state: KnowledgeUiState, viewModel: KnowledgeViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            label = { Text("搜索知识库") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = viewModel::search,
            enabled = !state.searching && state.searchQuery.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.searching) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("搜索")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.searchResults, key = { it.id }) { hit ->
                SearchHitCard(hit)
            }
        }
        if (state.searched && state.searchResults.isEmpty() && !state.searching) {
            Text(
                "无命中结果",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchHitCard(hit: KbSearchHit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("score: %.3f".format(hit.score), style = MaterialTheme.typography.labelSmall)
                Text(
                    hit.matchType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(hit.content, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
        }
    }
}

@Composable
private fun EntriesTab(
    state: KnowledgeUiState,
    viewModel: KnowledgeViewModel,
    onOpenEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loadingEntries) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.entries.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无条目，点右下角新建", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.entries, key = { it.id }) { entry ->
            Card(onClick = { onOpenEntry(entry.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.content, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "importance: %.2f".format(entry.importance),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (entry.tags.isNotEmpty()) {
                            Text(
                                entry.tags.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
        if (state.hasMore) {
            item {
                OutlinedButton(
                    onClick = { viewModel.loadEntries(reset = false) },
                    enabled = !state.loadingMore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loadingMore) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("加载更多")
                }
            }
        }
    }
}

@Composable
private fun CompileTab(state: KnowledgeUiState, viewModel: KnowledgeViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.compileSource,
            onValueChange = viewModel::updateCompileSource,
            label = { Text("编译来源（文本或 URL）") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        OutlinedButton(
            onClick = viewModel::submitCompile,
            enabled = !state.compiling && state.compileSource.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.compiling) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("提交编译")
        }
        state.compileJob?.let { job ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("作业 ${job.id}", style = MaterialTheme.typography.titleSmall)
                    Text("状态: ${job.status}", style = MaterialTheme.typography.bodyMedium)
                    if (job.total > 0) {
                        LinearProgressIndicator(
                            progress = { job.progress.toFloat() / job.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${job.progress}/${job.total}", style = MaterialTheme.typography.labelSmall)
                    }
                    job.error?.let { err ->
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateEntryDialog(
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onCreate: (content: String, source: String, tags: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var content by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("新建条目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容（必填）") },
                    minLines = 3,
                )
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("来源（可选）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("标签（逗号分隔）") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onCreate(content, source, tags)
                },
                enabled = !inProgress && content.isNotBlank(),
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ErrorBanner(
    text: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) { Text("知道了") }
            }
        }
    }
}

package com.nikolaspaci.app.llamallmlocal.ui.huggingface

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.data.huggingface.HfModel
import com.nikolaspaci.app.llamallmlocal.data.huggingface.HfModelDetail
import com.nikolaspaci.app.llamallmlocal.data.huggingface.HfSibling
import com.nikolaspaci.app.llamallmlocal.ui.common.SearchBar
import com.nikolaspaci.app.llamallmlocal.viewmodel.HuggingFaceErrorContext
import com.nikolaspaci.app.llamallmlocal.viewmodel.HuggingFaceUiState
import com.nikolaspaci.app.llamallmlocal.viewmodel.HuggingFaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuggingFaceScreen(
    viewModel: HuggingFaceViewModel,
    onNavigateBack: () -> Unit,
    onModelDownloaded: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Auto-navigate back on download complete
    LaunchedEffect(uiState) {
        if (uiState is HuggingFaceUiState.DownloadComplete) {
            onModelDownloaded((uiState as HuggingFaceUiState.DownloadComplete).filePath)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hf_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is HuggingFaceUiState.Initial -> {
                    SearchContent(
                        query = searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        onSearch = { viewModel.searchModels() }
                    )
                }
                is HuggingFaceUiState.Searching -> {
                    SearchingContent(query = searchQuery)
                }
                is HuggingFaceUiState.SearchResults -> {
                    SearchResultsContent(
                        query = searchQuery,
                        models = state.models,
                        onQueryChange = viewModel::updateSearchQuery,
                        onSearch = { viewModel.searchModels() },
                        onModelClick = { viewModel.selectModel(it.id) }
                    )
                }
                is HuggingFaceUiState.LoadingFiles -> {
                    LoadingFilesContent(modelId = state.modelId)
                }
                is HuggingFaceUiState.ModelFiles -> {
                    ModelFilesContent(
                        detail = state.detail,
                        onDownload = { file -> viewModel.downloadFile(state.detail.id, file.rfilename) },
                        onBack = { viewModel.goBackToSearchResults() }
                    )
                }
                is HuggingFaceUiState.Downloading -> {
                    DownloadingContent(
                        filename = state.filename,
                        bytesDownloaded = state.bytesDownloaded,
                        totalBytes = state.totalBytes,
                        progress = state.progress,
                        onCancel = { viewModel.cancelDownload() }
                    )
                }
                is HuggingFaceUiState.DownloadComplete -> {
                    DownloadCompleteContent()
                }
                is HuggingFaceUiState.Error -> {
                    val displayMessage = state.message ?: stringResource(
                        when (state.context) {
                            HuggingFaceErrorContext.SEARCH -> R.string.hf_search_failed
                            HuggingFaceErrorContext.LOAD_DETAILS -> R.string.hf_load_details_failed
                            HuggingFaceErrorContext.DOWNLOAD -> R.string.hf_error_title
                        }
                    )
                    ErrorContent(
                        message = displayMessage,
                        onRetry = state.retryAction,
                        onBack = onNavigateBack
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.hf_search_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.hf_search_placeholder),
            onSearch = onSearch
        )
    }
}


@Composable
private fun SearchingContent(query: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.hf_searching_for, query))
    }
}

@Composable
private fun SearchResultsContent(
    query: String,
    models: List<HfModel>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onModelClick: (HfModel) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.hf_search_placeholder),
            onSearch = onSearch
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (models.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.hf_no_models_found))
            }
        } else {
            Text(
                text = stringResource(R.string.hf_models_found, models.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(models) { model ->
                    ModelCard(model = model, onClick = { onModelClick(model) })
                }
            }
        }
    }
}

@Composable
private fun ModelCard(model: HfModel, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = model.id,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            model.author?.let {
                Text(
                    text = stringResource(R.string.hf_by_author, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.hf_downloads_count, formatDownloads(model.downloads)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingFilesContent(modelId: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.hf_loading_files, modelId))
    }
}

@Composable
private fun ModelFilesContent(
    detail: HfModelDetail,
    onDownload: (HfSibling) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.hf_back_to_results))
            }
            Text(
                text = detail.id,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        val ggufFiles = detail.ggufFiles
        if (ggufFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.hf_no_gguf_in_repo))
            }
        } else {
            Text(
                text = stringResource(R.string.hf_files_count, ggufFiles.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ggufFiles) { file ->
                    FileCard(file = file, onDownload = { onDownload(file) })
                }
            }
        }
    }
}

@Composable
private fun FileCard(file: HfSibling, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.rfilename,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                file.size?.let { size ->
                    Text(
                        text = formatFileSize(size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = stringResource(R.string.hf_download),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DownloadingContent(
    filename: String,
    bytesDownloaded: Long,
    totalBytes: Long,
    progress: Float,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.hf_downloading),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = filename,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (totalBytes > 0) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${formatFileSize(bytesDownloaded)} / ${formatFileSize(totalBytes)}",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatFileSize(bytesDownloaded),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.common_cancel))
        }
    }
}

@Composable
private fun DownloadCompleteContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.hf_download_complete))
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: (() -> Unit)?,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.hf_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.hf_go_back))
            }
            if (onRetry != null) {
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.common_retry))
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}

private fun formatDownloads(count: Int): String {
    return when {
        count < 1000 -> count.toString()
        count < 1_000_000 -> "%.1fK".format(count / 1000.0)
        else -> "%.1fM".format(count / 1_000_000.0)
    }
}

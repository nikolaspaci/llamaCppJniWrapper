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
import androidx.compose.foundation.lazy.LazyListScope
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
import com.nikolaspaci.app.llamallmlocal.data.curated.CuratedFacets
import com.nikolaspaci.app.llamallmlocal.data.curated.CuratedFilter
import com.nikolaspaci.app.llamallmlocal.data.huggingface.HfModelDetail
import com.nikolaspaci.app.llamallmlocal.data.huggingface.HfSibling
import com.nikolaspaci.app.llamallmlocal.ui.common.SearchBar
import com.nikolaspaci.app.llamallmlocal.viewmodel.AnnotatedHfModel
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
    val curatedFilter by viewModel.curatedFilter.collectAsState()
    val filteredCurated by viewModel.filteredCuratedModels.collectAsState()
    val filteredSearchResults by viewModel.filteredSearchResults.collectAsState()
    val curatedFacets by viewModel.curatedFacets.collectAsState()
    val searchInFlight by viewModel.searchInFlight.collectAsState()

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
            val state = uiState
            when {
                state is HuggingFaceUiState.Initial -> {
                    BrowseContent(
                        query = searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        onSearch = { viewModel.searchModels() },
                        curatedFilter = curatedFilter,
                        curatedFacets = curatedFacets,
                        onCuratedFilterChange = viewModel::updateCuratedFilter,
                        onCuratedClear = viewModel::clearCuratedFilter,
                        searchInFlight = searchInFlight
                    ) {
                        curatedModelSection(
                            models = filteredCurated,
                            onModelClick = viewModel::selectCuratedModel
                        )
                    }
                }
                state is HuggingFaceUiState.SearchResults -> {
                    BrowseContent(
                        query = searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        onSearch = { viewModel.searchModels() },
                        curatedFilter = curatedFilter,
                        curatedFacets = curatedFacets,
                        onCuratedFilterChange = viewModel::updateCuratedFilter,
                        onCuratedClear = viewModel::clearCuratedFilter,
                        searchInFlight = searchInFlight
                    ) {
                        searchResultsSection(
                            models = filteredSearchResults,
                            onModelClick = { viewModel.selectModel(it.model.id) }
                        )
                    }
                }
                state is HuggingFaceUiState.Error && state.context == HuggingFaceErrorContext.SEARCH -> {
                    val errorMessage = state.message ?: stringResource(R.string.hf_search_failed)
                    BrowseContent(
                        query = searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        onSearch = { viewModel.searchModels() },
                        curatedFilter = curatedFilter,
                        curatedFacets = curatedFacets,
                        onCuratedFilterChange = viewModel::updateCuratedFilter,
                        onCuratedClear = viewModel::clearCuratedFilter,
                        searchInFlight = searchInFlight
                    ) {
                        inlineSearchErrorItem(
                            message = errorMessage,
                            onRetry = state.retryAction
                        )
                    }
                }
                state is HuggingFaceUiState.LoadingFiles -> {
                    LoadingFilesContent(modelId = state.modelId)
                }
                state is HuggingFaceUiState.ModelFiles -> {
                    ModelFilesContent(
                        detail = state.detail,
                        approvedFilenames = state.approvedFilenames,
                        preselectedFilename = state.preselectedFilename,
                        onDownload = { file -> viewModel.downloadFile(state.detail.id, file.rfilename) },
                        onBack = { viewModel.goBackToSearchResults() }
                    )
                }
                state is HuggingFaceUiState.Downloading -> {
                    DownloadingContent(
                        filename = state.filename,
                        bytesDownloaded = state.bytesDownloaded,
                        totalBytes = state.totalBytes,
                        progress = state.progress,
                        onCancel = { viewModel.cancelDownload() }
                    )
                }
                state is HuggingFaceUiState.DownloadComplete -> {
                    DownloadCompleteContent()
                }
                state is HuggingFaceUiState.Error -> {
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

/**
 * Shared layout for the Initial and SearchResults states. The header (search bar + chip filters)
 * is always visible at the top; the body LazyColumn renders whatever section the caller provides.
 */
@Composable
private fun BrowseContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    curatedFilter: CuratedFilter,
    curatedFacets: CuratedFacets,
    onCuratedFilterChange: ((CuratedFilter) -> CuratedFilter) -> Unit,
    onCuratedClear: () -> Unit,
    searchInFlight: Boolean,
    body: LazyListScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.hf_search_placeholder),
            onSearch = onSearch
        )
        Spacer(Modifier.height(12.dp))
        CuratedFiltersBar(
            filter = curatedFilter,
            facets = curatedFacets,
            onFilterChange = onCuratedFilterChange,
            onClear = onCuratedClear
        )
        Spacer(Modifier.height(8.dp))
        // Thin in-flight indicator under the chips. Always reserve the slot so the
        // body below never shifts when the bar appears/disappears between debounced searches.
        Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
            if (searchInFlight) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            body()
        }
    }
}

private fun LazyListScope.inlineSearchErrorItem(
    message: String,
    onRetry: (() -> Unit)?
) {
    item(key = "inline_search_error") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (onRetry != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }
            }
        }
    }
}

fun LazyListScope.searchResultsSection(
    models: List<AnnotatedHfModel>,
    onModelClick: (AnnotatedHfModel) -> Unit
) {
    if (models.isEmpty()) {
        item(key = "search_results_empty") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.hf_no_models_found))
            }
        }
    } else {
        item(key = "search_results_count") {
            Text(
                text = stringResource(R.string.hf_models_found, models.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(models, key = { it.model.id }) { item ->
            ModelCard(item = item, onClick = { onModelClick(item) })
        }
    }
}

@Composable
private fun ModelCard(item: AnnotatedHfModel, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.model.id,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.isApproved) {
                    Spacer(Modifier.width(8.dp))
                    ApprovedBadge()
                }
            }
            item.curatedNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            item.model.author?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.hf_by_author, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.hf_downloads_count, formatDownloads(item.model.downloads)),
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
    approvedFilenames: Set<String>,
    preselectedFilename: String?,
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
            val sortedFiles = ggufFiles.sortedWith(
                compareByDescending<HfSibling> { it.rfilename == preselectedFilename }
                    .thenByDescending { it.rfilename in approvedFilenames }
                    .thenBy { it.rfilename }
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sortedFiles) { file ->
                    FileCard(
                        file = file,
                        isApproved = file.rfilename in approvedFilenames,
                        isPreselected = file.rfilename == preselectedFilename,
                        onDownload = { onDownload(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileCard(
    file: HfSibling,
    isApproved: Boolean,
    isPreselected: Boolean,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPreselected) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.rfilename,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isApproved) {
                        Spacer(Modifier.width(8.dp))
                        ApprovedBadge()
                    }
                }
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

internal fun formatFileSize(bytes: Long): String {
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

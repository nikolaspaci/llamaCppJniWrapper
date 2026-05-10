package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.curated.CuratedFacets
import com.nikolaspaci.app.llamallmlocal.data.curated.CuratedFilter
import com.nikolaspaci.app.llamallmlocal.data.curated.CuratedModel
import com.nikolaspaci.app.llamallmlocal.data.curated.CuratedModelRepository
import com.nikolaspaci.app.llamallmlocal.data.huggingface.DownloadState
import com.nikolaspaci.app.llamallmlocal.data.huggingface.HfModel
import com.nikolaspaci.app.llamallmlocal.data.huggingface.HfModelDetail
import com.nikolaspaci.app.llamallmlocal.data.huggingface.HuggingFaceApiClient
import com.nikolaspaci.app.llamallmlocal.data.huggingface.ModelDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val AUTO_SEARCH_MIN_CHARS = 3
private const val AUTO_SEARCH_DEBOUNCE_MS = 400L

enum class HuggingFaceErrorContext { SEARCH, LOAD_DETAILS, DOWNLOAD }

data class AnnotatedHfModel(
    val model: HfModel,
    val isApproved: Boolean,
    val curatedNotes: String? = null
)

sealed class HuggingFaceUiState {
    object Initial : HuggingFaceUiState()
    data class SearchResults(
        val query: String,
        val models: List<AnnotatedHfModel>
    ) : HuggingFaceUiState()
    data class LoadingFiles(val modelId: String) : HuggingFaceUiState()
    data class ModelFiles(
        val detail: HfModelDetail,
        val approvedFilenames: Set<String>,
        val preselectedFilename: String? = null
    ) : HuggingFaceUiState()
    data class Downloading(
        val repoId: String,
        val filename: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progress: Float
    ) : HuggingFaceUiState()
    data class DownloadComplete(val filePath: String) : HuggingFaceUiState()
    data class Error(
        val message: String?,
        val context: HuggingFaceErrorContext,
        val retryAction: (() -> Unit)?
    ) : HuggingFaceUiState()
}

@HiltViewModel
class HuggingFaceViewModel @Inject constructor(
    private val apiClient: HuggingFaceApiClient,
    private val downloadManager: ModelDownloadManager,
    private val curatedRepository: CuratedModelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HuggingFaceUiState>(HuggingFaceUiState.Initial)
    val uiState: StateFlow<HuggingFaceUiState> = _uiState.asStateFlow()

    /**
     * Orthogonal to [_uiState]: true while an HF search request is on the wire.
     * When true the screen overlays a thin LinearProgressIndicator under the chips
     * but keeps showing whatever body (curated section, previous results, or inline
     * error item) was already visible — no fullscreen spinner, no layout swap.
     */
    private val _searchInFlight = MutableStateFlow(false)
    val searchInFlight: StateFlow<Boolean> = _searchInFlight.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val curatedModels: StateFlow<List<CuratedModel>> = curatedRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _curatedFilter = MutableStateFlow(CuratedFilter())
    val curatedFilter: StateFlow<CuratedFilter> = _curatedFilter.asStateFlow()

    val filteredCuratedModels: StateFlow<List<CuratedModel>> =
        combine(curatedModels, _searchQuery, _curatedFilter) { all, query, filter ->
            curatedRepository.applyFilter(all, query, filter)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val curatedFacets: StateFlow<CuratedFacets> = curatedModels
        .map { curatedRepository.computeFacets(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CuratedFacets.EMPTY)

    /**
     * The HF search results filtered by the chip filter. When at least one chip is active,
     * non-approved results are hidden (they have no curated metadata to match on).
     * When no chip is active, all HF results are returned unchanged.
     */
    val filteredSearchResults: StateFlow<List<AnnotatedHfModel>> =
        combine(_uiState, curatedModels, _curatedFilter) { state, curated, filter ->
            if (state !is HuggingFaceUiState.SearchResults) return@combine emptyList()
            if (filter.isEmpty) return@combine state.models
            val curatedByRepo: Map<String, List<CuratedModel>> = curated.groupBy { it.hfRepoId }
            state.models.filter { annotated ->
                val matches = curatedByRepo[annotated.model.id] ?: return@filter false
                matches.any { c ->
                    (filter.paramsLabels.isEmpty() || c.paramsLabel in filter.paramsLabels) &&
                        (filter.quantizations.isEmpty() || c.quantization in filter.quantizations) &&
                        (filter.providers.isEmpty() || c.provider in filter.providers)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val approvedRepoIds: Set<String>
        get() = curatedModels.value.map { it.hfRepoId }.toSet()

    private fun approvedFilenamesFor(repoId: String): Set<String> =
        curatedModels.value.filter { it.hfRepoId == repoId }.map { it.filename }.toSet()

    private fun curatedNotesFor(repoId: String): String? =
        curatedModels.value.firstOrNull { it.hfRepoId == repoId && it.notes.isNotBlank() }?.notes

    private var downloadJob: Job? = null
    private var lastQuery: String = ""

    @OptIn(FlowPreview::class)
    private val autoSearchJob: Job = _searchQuery
        .debounce(AUTO_SEARCH_DEBOUNCE_MS)
        .map { it.trim() }
        .distinctUntilChanged()
        .map { query ->
            val current = _uiState.value
            val canAutoSearch = current is HuggingFaceUiState.Initial ||
                current is HuggingFaceUiState.SearchResults ||
                (current is HuggingFaceUiState.Error && current.context == HuggingFaceErrorContext.SEARCH)
            if (!canAutoSearch) return@map
            when {
                query.length >= AUTO_SEARCH_MIN_CHARS -> {
                    if (query != lastQuery) searchModels(query)
                }
                current is HuggingFaceUiState.SearchResults -> {
                    _uiState.value = HuggingFaceUiState.Initial
                    lastQuery = ""
                }
            }
        }
        .launchIn(viewModelScope)

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateCuratedFilter(transform: (CuratedFilter) -> CuratedFilter) {
        _curatedFilter.value = transform(_curatedFilter.value)
    }

    fun clearCuratedFilter() {
        _curatedFilter.value = CuratedFilter()
    }

    fun searchModels(query: String = _searchQuery.value) {
        if (query.isBlank()) return
        lastQuery = query
        _searchInFlight.value = true

        viewModelScope.launch {
            val result = apiClient.searchModels(query)
            result.fold(
                onSuccess = { models ->
                    _uiState.value = HuggingFaceUiState.SearchResults(
                        query = query,
                        models = annotateAndSort(models)
                    )
                },
                onFailure = { error ->
                    _uiState.value = HuggingFaceUiState.Error(
                        message = error.message,
                        context = HuggingFaceErrorContext.SEARCH,
                        retryAction = { searchModels(query) }
                    )
                }
            )
            _searchInFlight.value = false
        }
    }

    private fun annotateAndSort(models: List<HfModel>): List<AnnotatedHfModel> {
        val approved = approvedRepoIds
        return models
            .map { hf ->
                val isApproved = hf.id in approved
                AnnotatedHfModel(
                    model = hf,
                    isApproved = isApproved,
                    curatedNotes = if (isApproved) curatedNotesFor(hf.id) else null
                )
            }
            .sortedWith(
                compareByDescending<AnnotatedHfModel> { it.isApproved }
                    .thenByDescending { it.model.downloads }
            )
    }

    fun selectModel(modelId: String, preselectedFilename: String? = null) {
        _uiState.value = HuggingFaceUiState.LoadingFiles(modelId)

        viewModelScope.launch {
            val result = apiClient.getModelDetail(modelId)
            result.fold(
                onSuccess = { detail ->
                    _uiState.value = HuggingFaceUiState.ModelFiles(
                        detail = detail,
                        approvedFilenames = approvedFilenamesFor(detail.id),
                        preselectedFilename = preselectedFilename
                    )
                },
                onFailure = { error ->
                    _uiState.value = HuggingFaceUiState.Error(
                        message = error.message,
                        context = HuggingFaceErrorContext.LOAD_DETAILS,
                        retryAction = { selectModel(modelId, preselectedFilename) }
                    )
                }
            )
        }
    }

    fun selectCuratedModel(curated: CuratedModel) {
        selectModel(curated.hfRepoId, preselectedFilename = curated.filename)
    }

    fun downloadFile(repoId: String, filename: String) {
        downloadJob = viewModelScope.launch {
            downloadManager.downloadFile(repoId, filename).collect { state ->
                when (state) {
                    is DownloadState.Idle -> {
                        _uiState.value = HuggingFaceUiState.Downloading(repoId, filename, 0, 0, 0f)
                    }
                    is DownloadState.Downloading -> {
                        _uiState.value = HuggingFaceUiState.Downloading(
                            repoId, filename, state.bytesDownloaded, state.totalBytes, state.progress
                        )
                    }
                    is DownloadState.Completed -> {
                        _uiState.value = HuggingFaceUiState.DownloadComplete(state.filePath)
                    }
                    is DownloadState.Failed -> {
                        _uiState.value = HuggingFaceUiState.Error(
                            message = state.error,
                            context = HuggingFaceErrorContext.DOWNLOAD,
                            retryAction = { downloadFile(repoId, filename) }
                        )
                    }
                    is DownloadState.Cancelled -> {
                        selectModel(repoId)
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        val currentState = _uiState.value
        downloadJob?.cancel()
        downloadJob = null
        if (currentState is HuggingFaceUiState.Downloading) {
            selectModel(currentState.repoId)
        }
    }

    fun goBackToSearchResults() {
        if (lastQuery.isNotBlank()) {
            searchModels(lastQuery)
        } else {
            _uiState.value = HuggingFaceUiState.Initial
        }
    }
}

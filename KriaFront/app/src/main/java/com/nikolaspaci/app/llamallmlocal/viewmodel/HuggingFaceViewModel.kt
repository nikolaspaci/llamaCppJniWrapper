package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.huggingface.DownloadState
import com.nikolaspaci.app.llamallmlocal.data.huggingface.HfModel
import com.nikolaspaci.app.llamallmlocal.data.huggingface.HfModelDetail
import com.nikolaspaci.app.llamallmlocal.data.huggingface.HfSibling
import com.nikolaspaci.app.llamallmlocal.data.huggingface.HuggingFaceApiClient
import com.nikolaspaci.app.llamallmlocal.data.huggingface.ModelDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HuggingFaceErrorContext { SEARCH, LOAD_DETAILS, DOWNLOAD }

sealed class HuggingFaceUiState {
    object Initial : HuggingFaceUiState()
    object Searching : HuggingFaceUiState()
    data class SearchResults(val query: String, val models: List<HfModel>) : HuggingFaceUiState()
    data class LoadingFiles(val modelId: String) : HuggingFaceUiState()
    data class ModelFiles(val detail: HfModelDetail) : HuggingFaceUiState()
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
    private val downloadManager: ModelDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<HuggingFaceUiState>(HuggingFaceUiState.Initial)
    val uiState: StateFlow<HuggingFaceUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var downloadJob: Job? = null
    private var lastQuery: String = ""

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun searchModels(query: String = _searchQuery.value) {
        if (query.isBlank()) return
        lastQuery = query
        _uiState.value = HuggingFaceUiState.Searching

        viewModelScope.launch {
            val result = apiClient.searchModels(query)
            result.fold(
                onSuccess = { models ->
                    _uiState.value = HuggingFaceUiState.SearchResults(query, models)
                },
                onFailure = { error ->
                    _uiState.value = HuggingFaceUiState.Error(
                        message = error.message,
                        context = HuggingFaceErrorContext.SEARCH,
                        retryAction = { searchModels(query) }
                    )
                }
            )
        }
    }

    fun selectModel(modelId: String) {
        _uiState.value = HuggingFaceUiState.LoadingFiles(modelId)

        viewModelScope.launch {
            val result = apiClient.getModelDetail(modelId)
            result.fold(
                onSuccess = { detail ->
                    _uiState.value = HuggingFaceUiState.ModelFiles(detail)
                },
                onFailure = { error ->
                    _uiState.value = HuggingFaceUiState.Error(
                        message = error.message,
                        context = HuggingFaceErrorContext.LOAD_DETAILS,
                        retryAction = { selectModel(modelId) }
                    )
                }
            )
        }
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
                        // Go back to model files - re-fetch
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

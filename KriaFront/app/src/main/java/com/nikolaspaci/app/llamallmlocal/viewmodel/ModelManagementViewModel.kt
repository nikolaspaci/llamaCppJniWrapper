package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.repository.CachedModelEntry
import com.nikolaspaci.app.llamallmlocal.data.repository.DeleteResult
import com.nikolaspaci.app.llamallmlocal.data.repository.ModelCacheRepository
import com.nikolaspaci.app.llamallmlocal.engine.ModelEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelManagementViewModel @Inject constructor(
    private val cacheRepository: ModelCacheRepository,
    private val engine: ModelEngine
) : ViewModel() {

    private val _entries = MutableStateFlow<List<CachedModelEntry>>(emptyList())
    val entries: StateFlow<List<CachedModelEntry>> = _entries.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _activeModelPath = MutableStateFlow<String?>(null)
    val activeModelPath: StateFlow<String?> = _activeModelPath.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _activeModelPath.value = engine.getCurrentModelPath()
            _entries.value = cacheRepository.list()
            _selected.value = _selected.value.intersect(_entries.value.map { it.absolutePath }.toSet())
        }
    }

    fun toggleSelection(path: String) {
        val current = _selected.value
        _selected.value = if (path in current) current - path else current + path
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun deleteSelected(onComplete: (DeleteResult) -> Unit) {
        val toDelete = _selected.value
        if (toDelete.isEmpty() || _isDeleting.value) return
        _isDeleting.value = true
        viewModelScope.launch {
            val active = _activeModelPath.value
            val deletingActive = active != null && active in toDelete
            val deletingActiveSibling = active != null && toDelete.any { path ->
                java.io.File(path).parentFile?.absolutePath == java.io.File(active).parentFile?.absolutePath
            }
            if (deletingActive || deletingActiveSibling) {
                runCatching { engine.stopPredict() }
                runCatching { engine.unloadModel() }
            }
            val result = cacheRepository.delete(toDelete)
            _selected.value = emptySet()
            _activeModelPath.value = engine.getCurrentModelPath()
            _entries.value = cacheRepository.list()
            _isDeleting.value = false
            onComplete(result)
        }
    }
}

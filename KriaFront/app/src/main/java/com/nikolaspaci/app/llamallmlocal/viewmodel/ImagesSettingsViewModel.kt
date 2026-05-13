package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.repository.ImageCacheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImagesSettingsViewModel @Inject constructor(
    private val imageCacheRepository: ImageCacheRepository
) : ViewModel() {

    sealed class State {
        data object Loading : State()
        data class Idle(val sizeBytes: Long, val count: Int) : State()
        data object Clearing : State()
        data class Error(val message: String) : State()
    }

    sealed class Event {
        data class Cleared(val deletedCount: Int, val freedBytes: Long) : Event()
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _confirmVisible = MutableStateFlow(false)
    val confirmVisible: StateFlow<Boolean> = _confirmVisible.asStateFlow()

    private val _lastEvent = MutableStateFlow<Event?>(null)
    val lastEvent: StateFlow<Event?> = _lastEvent.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { imageCacheRepository.stats() }
                .onSuccess { stats ->
                    _state.value = State.Idle(stats.totalBytes, stats.count)
                }
                .onFailure { e ->
                    _state.value = State.Error(e.message ?: "Erreur inconnue")
                }
        }
    }

    fun requestClear() {
        val current = _state.value
        if (current is State.Idle && current.count > 0) {
            _confirmVisible.value = true
        }
    }

    fun dismissClear() {
        _confirmVisible.value = false
    }

    fun confirmClear() {
        _confirmVisible.value = false
        _state.value = State.Clearing
        viewModelScope.launch {
            runCatching { imageCacheRepository.clearAll() }
                .onSuccess { result ->
                    _lastEvent.value = Event.Cleared(result.deletedCount, result.freedBytes)
                    refresh()
                }
                .onFailure { e ->
                    _state.value = State.Error(e.message ?: "Erreur inconnue")
                }
        }
    }

    fun consumeEvent() {
        _lastEvent.value = null
    }
}

package com.nikolaspaci.app.llamallmlocal.viewmodel

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.WhisperModelManager
import com.nikolaspaci.app.llamallmlocal.data.audio.AudioRecorder
import com.nikolaspaci.app.llamallmlocal.data.huggingface.DownloadState
import com.nikolaspaci.app.llamallmlocal.data.huggingface.WhisperModelDownloader
import com.nikolaspaci.app.llamallmlocal.data.huggingface.WhisperModelVariant
import com.nikolaspaci.app.llamallmlocal.engine.SpeechEngine
import com.nikolaspaci.app.llamallmlocal.engine.TranscriptionEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SpeechInputViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val whisperModelManager: WhisperModelManager,
    private val whisperDownloader: WhisperModelDownloader,
    private val speechEngine: SpeechEngine,
    private val prefs: SharedPreferences
) : ViewModel() {

    sealed class UiState {
        data object Idle : UiState()
        data class Downloading(val progress: Float, val bytes: Long, val total: Long) : UiState()
        data object Recording : UiState()
        data object Transcribing : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _transcribed = MutableStateFlow<String?>(null)
    val transcribed: StateFlow<String?> = _transcribed.asStateFlow()

    private val _variantPickerVisible = MutableStateFlow(false)
    val variantPickerVisible: StateFlow<Boolean> = _variantPickerVisible.asStateFlow()

    private var currentJob: Job? = null
    private var pendingLanguage: String = "auto"

    fun hasMicrophonePermission(): Boolean = audioRecorder.hasPermission()

    fun hasWhisperModel(): Boolean = whisperModelManager.hasAnyModel()

    /**
     * Entry point. Flow:
     *  - model on disk          -> record immediately
     *  - preference saved       -> download saved variant, then record
     *  - first time, nothing    -> show picker dialog, deferred until user picks
     */
    fun startRecording(language: String = "auto") {
        if (_state.value !is UiState.Idle) return
        pendingLanguage = language

        val existing = whisperModelManager.defaultModelOrNull()
        if (existing != null) {
            currentJob = viewModelScope.launch { runRecording(existing, language) }
            return
        }

        val savedVariant = readSavedVariant()
        if (savedVariant != null) {
            currentJob = viewModelScope.launch {
                val model = downloadVariant(savedVariant) ?: return@launch
                runRecording(model, language)
            }
            return
        }

        _variantPickerVisible.value = true
    }

    fun chooseVariant(variant: WhisperModelVariant) {
        _variantPickerVisible.value = false
        saveVariant(variant)
        currentJob = viewModelScope.launch {
            val model = downloadVariant(variant) ?: return@launch
            runRecording(model, pendingLanguage)
        }
    }

    fun dismissVariantPicker() {
        _variantPickerVisible.value = false
        _state.value = UiState.Idle
    }

    private suspend fun downloadVariant(variant: WhisperModelVariant): File? {
        var resolved: File? = null
        whisperDownloader.download(variant).collect { ds ->
            when (ds) {
                is DownloadState.Idle -> _state.value = UiState.Downloading(0f, 0, 0)
                is DownloadState.Downloading ->
                    _state.value = UiState.Downloading(ds.progress, ds.bytesDownloaded, ds.totalBytes)
                is DownloadState.Completed -> resolved = File(ds.filePath)
                is DownloadState.Failed -> _state.value = UiState.Error("Telechargement echoue: ${ds.error}")
                is DownloadState.Cancelled -> _state.value = UiState.Idle
            }
        }
        return resolved
    }

    private suspend fun runRecording(model: File, language: String) {
        try {
            _state.value = UiState.Recording
            val pcm = audioRecorder.record()
            if (pcm.isEmpty()) {
                _state.value = UiState.Idle
                return
            }

            _state.value = UiState.Transcribing
            if (!speechEngine.isLoaded() || speechEngine.getCurrentModelPath() != model.absolutePath) {
                val loaded = speechEngine.loadModel(model.absolutePath)
                if (loaded.isFailure) {
                    _state.value = UiState.Error(
                        loaded.exceptionOrNull()?.message ?: "Echec chargement modele"
                    )
                    return
                }
            }

            speechEngine.transcribe(pcm, language).collect { event ->
                when (event) {
                    is TranscriptionEvent.Segment -> { /* partials reserved for future */ }
                    is TranscriptionEvent.Completed -> {
                        _transcribed.value = event.fullText.trim()
                        _state.value = UiState.Idle
                    }
                    is TranscriptionEvent.Error -> {
                        _state.value = UiState.Error(event.message)
                    }
                }
            }
        } catch (e: SecurityException) {
            _state.value = UiState.Error("Permission micro refusee")
        } catch (e: Exception) {
            _state.value = UiState.Error(e.message ?: "Erreur inconnue")
        }
    }

    fun stopRecording() {
        audioRecorder.stop()
    }

    fun cancel() {
        audioRecorder.stop()
        speechEngine.stop()
        currentJob?.cancel()
        currentJob = null
        _variantPickerVisible.value = false
        _state.value = UiState.Idle
    }

    fun consumeTranscribed() {
        _transcribed.value = null
    }

    fun dismissError() {
        if (_state.value is UiState.Error) {
            _state.value = UiState.Idle
        }
    }

    private fun readSavedVariant(): WhisperModelVariant? {
        val raw = prefs.getString(PREF_KEY_VARIANT, null) ?: return null
        return runCatching { WhisperModelVariant.valueOf(raw) }.getOrNull()
    }

    private fun saveVariant(variant: WhisperModelVariant) {
        prefs.edit { putString(PREF_KEY_VARIANT, variant.name) }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stop()
        speechEngine.stop()
    }

    companion object {
        private const val PREF_KEY_VARIANT = "whisper_model_variant"
    }
}

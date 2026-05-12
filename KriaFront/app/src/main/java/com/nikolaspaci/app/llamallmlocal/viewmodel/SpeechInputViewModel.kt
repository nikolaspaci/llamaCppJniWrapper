package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.WhisperModelManager
import com.nikolaspaci.app.llamallmlocal.data.audio.AudioRecorder
import com.nikolaspaci.app.llamallmlocal.engine.SpeechEngine
import com.nikolaspaci.app.llamallmlocal.engine.TranscriptionEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpeechInputViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val whisperModelManager: WhisperModelManager,
    private val speechEngine: SpeechEngine
) : ViewModel() {

    sealed class UiState {
        data object Idle : UiState()
        data object Recording : UiState()
        data object Transcribing : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _transcribed = MutableStateFlow<String?>(null)
    val transcribed: StateFlow<String?> = _transcribed.asStateFlow()

    private var recordingJob: Job? = null

    fun hasMicrophonePermission(): Boolean = audioRecorder.hasPermission()

    fun hasWhisperModel(): Boolean = whisperModelManager.hasAnyModel()

    fun startRecording(language: String = "auto") {
        if (_state.value !is UiState.Idle) return

        val model = whisperModelManager.defaultModelOrNull()
        if (model == null) {
            _state.value = UiState.Error("Aucun modele whisper installe")
            return
        }

        recordingJob = viewModelScope.launch {
            try {
                _state.value = UiState.Recording
                val pcm = audioRecorder.record()
                if (pcm.isEmpty()) {
                    _state.value = UiState.Idle
                    return@launch
                }

                _state.value = UiState.Transcribing
                if (!speechEngine.isLoaded() || speechEngine.getCurrentModelPath() != model.absolutePath) {
                    val loaded = speechEngine.loadModel(model.absolutePath)
                    if (loaded.isFailure) {
                        _state.value = UiState.Error(
                            loaded.exceptionOrNull()?.message ?: "Echec chargement modele"
                        )
                        return@launch
                    }
                }

                speechEngine.transcribe(pcm, language).collect { event ->
                    when (event) {
                        is TranscriptionEvent.Segment -> { /* could show partials */ }
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
    }

    /** User tapped the validate button - stops recording so transcription can proceed. */
    fun stopRecording() {
        audioRecorder.stop()
    }

    /** User tapped cancel - aborts everything. */
    fun cancel() {
        audioRecorder.stop()
        speechEngine.stop()
        recordingJob?.cancel()
        recordingJob = null
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

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stop()
        speechEngine.stop()
    }
}

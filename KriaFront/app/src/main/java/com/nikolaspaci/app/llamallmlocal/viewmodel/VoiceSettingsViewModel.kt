package com.nikolaspaci.app.llamallmlocal.viewmodel

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.WhisperModelManager
import com.nikolaspaci.app.llamallmlocal.data.huggingface.DownloadState
import com.nikolaspaci.app.llamallmlocal.data.huggingface.WhisperModelDownloader
import com.nikolaspaci.app.llamallmlocal.data.huggingface.WhisperModelVariant
import com.nikolaspaci.app.llamallmlocal.engine.SpeechEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val whisperModelManager: WhisperModelManager,
    private val whisperDownloader: WhisperModelDownloader,
    private val speechEngine: SpeechEngine,
    private val prefs: SharedPreferences
) : ViewModel() {

    sealed class VoiceSectionState {
        data object Empty : VoiceSectionState()
        data class Installed(
            val variant: WhisperModelVariant,
            val sizeBytes: Long
        ) : VoiceSectionState()
        data class Downloading(
            val variant: WhisperModelVariant,
            val progress: Float,
            val bytes: Long,
            val total: Long
        ) : VoiceSectionState()
        data class Error(val message: String) : VoiceSectionState()
    }

    private val _voiceSection = MutableStateFlow<VoiceSectionState>(VoiceSectionState.Empty)
    val voiceSection: StateFlow<VoiceSectionState> = _voiceSection.asStateFlow()

    private val _pickerVisible = MutableStateFlow(false)
    val pickerVisible: StateFlow<Boolean> = _pickerVisible.asStateFlow()

    private val _deleteConfirmVisible = MutableStateFlow(false)
    val deleteConfirmVisible: StateFlow<Boolean> = _deleteConfirmVisible.asStateFlow()

    private var currentJob: Job? = null

    init {
        refreshVoiceState()
    }

    fun refreshVoiceState() {
        val file = whisperModelManager.defaultModelOrNull()
        _voiceSection.value = if (file != null) {
            VoiceSectionState.Installed(
                variant = variantFromFile(file) ?: WhisperModelVariant.BASE,
                sizeBytes = file.length()
            )
        } else {
            VoiceSectionState.Empty
        }
    }

    fun requestChange() {
        if (_voiceSection.value is VoiceSectionState.Downloading) return
        _pickerVisible.value = true
    }

    fun dismissPicker() {
        _pickerVisible.value = false
    }

    fun chooseVariant(variant: WhisperModelVariant) {
        _pickerVisible.value = false
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                speechEngine.unloadModel()
                whisperModelManager.deleteAll()
            }
            prefs.edit { putString(PREF_KEY_VARIANT, variant.name) }
            whisperDownloader.download(variant).collect { ds ->
                when (ds) {
                    is DownloadState.Idle ->
                        _voiceSection.value = VoiceSectionState.Downloading(variant, 0f, 0, 0)
                    is DownloadState.Downloading ->
                        _voiceSection.value = VoiceSectionState.Downloading(
                            variant, ds.progress, ds.bytesDownloaded, ds.totalBytes
                        )
                    is DownloadState.Completed -> {
                        val f = File(ds.filePath)
                        _voiceSection.value = VoiceSectionState.Installed(variant, f.length())
                    }
                    is DownloadState.Failed -> {
                        _voiceSection.value = VoiceSectionState.Error(ds.error)
                        refreshVoiceState()
                    }
                    is DownloadState.Cancelled -> refreshVoiceState()
                }
            }
        }
    }

    fun cancelDownload() {
        currentJob?.cancel()
        currentJob = null
        refreshVoiceState()
    }

    fun requestDelete() {
        if (_voiceSection.value is VoiceSectionState.Installed) {
            _deleteConfirmVisible.value = true
        }
    }

    fun dismissDelete() {
        _deleteConfirmVisible.value = false
    }

    fun confirmDelete() {
        _deleteConfirmVisible.value = false
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                speechEngine.unloadModel()
                whisperModelManager.deleteAll()
            }
            prefs.edit { remove(PREF_KEY_VARIANT) }
            refreshVoiceState()
        }
    }

    private fun variantFromFile(file: File): WhisperModelVariant? {
        val name = file.name
        return WhisperModelVariant.values().firstOrNull { it.fileName == name }
    }

    fun currentVariant(): WhisperModelVariant? {
        val saved = prefs.getString(PREF_KEY_VARIANT, null) ?: return null
        return runCatching { WhisperModelVariant.valueOf(saved) }.getOrNull()
    }

    companion object {
        private const val PREF_KEY_VARIANT = "whisper_model_variant"
    }
}

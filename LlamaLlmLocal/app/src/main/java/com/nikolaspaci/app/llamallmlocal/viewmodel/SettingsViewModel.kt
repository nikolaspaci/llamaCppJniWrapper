package com.nikolaspaci.app.llamallmlocal.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.jni.LlamaJniService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

const val MODEL_PATH_KEY = "model_path"

class SettingsViewModel(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val llamaJniService: LlamaJniService
) : ViewModel() {

    private val _cachedModels = MutableStateFlow<List<File>>(emptyList())
    val cachedModels: StateFlow<List<File>> = _cachedModels.asStateFlow()

    init {
        loadCachedModels()
    }

    fun loadCachedModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = context.cacheDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
                ?: emptyList()
            _cachedModels.value = files
        }
    }

    fun cacheModel(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileName = "model_${System.currentTimeMillis()}.gguf"
            val outputFile = File(context.cacheDir, fileName)
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                // Reload the list of cached models
                loadCachedModels()
                withContext(Dispatchers.Main) {
                    onResult(outputFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }

    fun saveModelPath(path: String) {
        sharedPreferences.edit().putString(MODEL_PATH_KEY, path).apply()
        llamaJniService.loadModel(path)
    }

    fun getModelPath(): String? {
        return sharedPreferences.getString(MODEL_PATH_KEY, null)
    }
}
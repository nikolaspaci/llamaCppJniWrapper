package com.nikolaspaci.app.llamallmlocal.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.ModelStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

const val MODEL_PATH_KEY = "model_path"

class ModelFileViewModel(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val storageManager = ModelStorageManager(context)

    private val _cachedModels = MutableStateFlow<List<File>>(emptyList())
    val cachedModels: StateFlow<List<File>> = _cachedModels.asStateFlow()

    init {
        loadCachedModels()
    }

    fun loadCachedModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _cachedModels.value = storageManager.listAllModels()
        }
    }

    fun cacheModel(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                }

                if (fileName == null) {
                    withContext(Dispatchers.Main) { onResult(null) }
                    return@launch
                }

                val outputFile = storageManager.modelFile(fileName)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
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

    fun cacheVisionAdapter(uri: Uri, forModelPath: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                }

                if (fileName == null) {
                    withContext(Dispatchers.Main) { onResult(false) }
                    return@launch
                }

                val targetDir = File(forModelPath).parentFile ?: storageManager.modelsRoot()
                val outputFile = File(targetDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                withContext(Dispatchers.Main) { onResult(true) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun hasVisionAdapter(modelPath: String): Boolean {
        return storageManager.hasVisionAdapter(modelPath)
    }

    fun saveModelPath(path: String) {
        sharedPreferences.edit().putString(MODEL_PATH_KEY, path).apply()
    }

    fun getModelPath(): String? {
        val savedPath = sharedPreferences.getString(MODEL_PATH_KEY, null) ?: return null
        if (!File(savedPath).exists()) {
            sharedPreferences.edit().remove(MODEL_PATH_KEY).apply()
            return null
        }
        return savedPath
    }
}

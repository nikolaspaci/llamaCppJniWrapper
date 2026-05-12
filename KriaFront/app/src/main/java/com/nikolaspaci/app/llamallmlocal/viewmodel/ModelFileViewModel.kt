package com.nikolaspaci.app.llamallmlocal.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.ModelStorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

const val MODEL_PATH_KEY = "model_path"

sealed class ImportState {
    data object Idle : ImportState()
    data class Copying(
        val fileName: String,
        val bytesCopied: Long,
        val totalBytes: Long
    ) : ImportState()
}

class ModelFileViewModel(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val storageManager = ModelStorageManager(context)

    private val _cachedModels = MutableStateFlow<List<File>>(emptyList())
    val cachedModels: StateFlow<List<File>> = _cachedModels.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private var importJob: Job? = null

    init {
        loadCachedModels()
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    private suspend fun copyWithProgress(
        uri: Uri,
        outputFile: File,
        fileName: String,
        totalBytes: Long
    ) {
        val progressThrottle = 1L * 1024 * 1024 // 1 MB
        var bytesCopied = 0L
        var lastEmitted = 0L
        _importState.value = ImportState.Copying(fileName, 0L, totalBytes)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outputFile).use { output ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    currentCoroutineContext().ensureActive()
                    output.write(buf, 0, read)
                    bytesCopied += read
                    if (bytesCopied - lastEmitted >= progressThrottle) {
                        _importState.value = ImportState.Copying(fileName, bytesCopied, totalBytes)
                        lastEmitted = bytesCopied
                    }
                }
            }
        }
        _importState.value = ImportState.Copying(fileName, bytesCopied, totalBytes)
    }

    private fun queryFileMeta(uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size: Long = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return name to size
    }

    fun loadCachedModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _cachedModels.value = storageManager.listAllModels()
        }
    }

    fun cacheModel(uri: Uri, onResult: (String?) -> Unit) {
        importJob?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            var outputFile: File? = null
            try {
                val (fileName, totalBytes) = queryFileMeta(uri)
                if (fileName == null) {
                    withContext(Dispatchers.Main) { onResult(null) }
                    return@launch
                }
                outputFile = storageManager.modelFile(fileName)
                copyWithProgress(uri, outputFile, fileName, totalBytes)
                loadCachedModels()
                withContext(Dispatchers.Main) {
                    onResult(outputFile.absolutePath)
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    outputFile?.takeIf { it.exists() }?.let {
                        try { it.delete() } catch (_: SecurityException) { /* ignore */ }
                    }
                    withContext(Dispatchers.Main) { onResult(null) }
                }
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                outputFile?.takeIf { it.exists() }?.let {
                    try { it.delete() } catch (_: SecurityException) { /* ignore */ }
                }
                withContext(Dispatchers.Main) { onResult(null) }
            } finally {
                _importState.value = ImportState.Idle
            }
        }
    }

    fun cacheVisionAdapter(uri: Uri, forModelPath: String, onResult: (Boolean) -> Unit) {
        importJob?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            var outputFile: File? = null
            try {
                val (fileName, totalBytes) = queryFileMeta(uri)
                if (fileName == null) {
                    withContext(Dispatchers.Main) { onResult(false) }
                    return@launch
                }
                val targetDir = File(forModelPath).parentFile ?: storageManager.modelsRoot()
                outputFile = File(targetDir, fileName)
                copyWithProgress(uri, outputFile, fileName, totalBytes)
                withContext(Dispatchers.Main) { onResult(true) }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    outputFile?.takeIf { it.exists() }?.let {
                        try { it.delete() } catch (_: SecurityException) { /* ignore */ }
                    }
                    withContext(Dispatchers.Main) { onResult(false) }
                }
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onResult(false) }
            } finally {
                _importState.value = ImportState.Idle
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

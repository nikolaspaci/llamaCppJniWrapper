package com.nikolaspaci.app.llamallmlocal.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun whisperRoot(): File {
        val root = File(context.cacheDir, "whisper")
        if (!root.exists()) root.mkdirs()
        return root
    }

    fun whisperModelFile(fileName: String): File = File(whisperRoot(), fileName)

    fun listWhisperModels(): List<File> {
        val root = whisperRoot()
        if (!root.exists()) return emptyList()
        return root.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".bin", ignoreCase = true) }
            ?: emptyList()
    }

    fun defaultModelOrNull(): File? = listWhisperModels().firstOrNull()

    fun hasAnyModel(): Boolean = listWhisperModels().isNotEmpty()

    /**
     * Remove all whisper models and partial downloads under [whisperRoot].
     * Returns true if the directory is empty afterwards.
     */
    fun deleteAll(): Boolean {
        val root = whisperRoot()
        val files = root.listFiles() ?: return true
        var allDeleted = true
        for (f in files) {
            if (f.isFile) {
                try {
                    if (!f.delete()) allDeleted = false
                } catch (_: SecurityException) {
                    allDeleted = false
                }
            }
        }
        return allDeleted
    }
}

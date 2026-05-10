package com.nikolaspaci.app.llamallmlocal.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun modelsRoot(): File {
        val root = File(context.cacheDir, "models")
        if (!root.exists()) root.mkdirs()
        return root
    }

    fun modelDir(modelFileName: String): File {
        val nameWithoutExtension = modelFileName.removeSuffix(".gguf").removeSuffix(".GGUF")
        val dir = File(modelsRoot(), nameWithoutExtension)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun modelFile(modelFileName: String): File {
        return File(modelDir(modelFileName), modelFileName)
    }

    fun listAllModels(): List<File> {
        val root = modelsRoot()
        if (!root.exists()) return emptyList()

        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.flatMap { dir ->
                dir.listFiles()?.filter { file ->
                    file.isFile &&
                    file.name.endsWith(".gguf", ignoreCase = true) &&
                    !file.name.contains("mmproj", ignoreCase = true)
                } ?: emptyList()
            }
            ?: emptyList()
    }

    fun listAllFilesIncludingMmproj(): List<File> {
        val root = modelsRoot()
        if (!root.exists()) return emptyList()

        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.flatMap { dir ->
                dir.listFiles()?.filter { file ->
                    file.isFile && file.name.endsWith(".gguf", ignoreCase = true)
                } ?: emptyList()
            }
            ?: emptyList()
    }

    fun hasVisionAdapter(modelPath: String): Boolean {
        val parentDir = File(modelPath).parentFile ?: return false
        return parentDir.listFiles()?.any { file ->
            file.isFile &&
            file.name.contains("mmproj", ignoreCase = true) &&
            file.name.endsWith(".gguf", ignoreCase = true)
        } == true
    }

    fun siblingMmprojFor(modelFile: File): File? {
        val parentDir = modelFile.parentFile ?: return null
        return parentDir.listFiles()?.firstOrNull { file ->
            file.isFile &&
            file.name.contains("mmproj", ignoreCase = true) &&
            file.name.endsWith(".gguf", ignoreCase = true)
        }
    }

    fun deleteModel(file: File): Boolean {
        if (!file.exists()) return true
        val parent = file.parentFile
        val deleted = try {
            file.delete()
        } catch (_: SecurityException) {
            false
        }
        if (deleted && parent != null && parent != modelsRoot()) {
            val remaining = parent.listFiles()
            if (remaining == null || remaining.isEmpty()) {
                try { parent.delete() } catch (_: SecurityException) { /* ignore */ }
            }
        }
        return deleted
    }
}

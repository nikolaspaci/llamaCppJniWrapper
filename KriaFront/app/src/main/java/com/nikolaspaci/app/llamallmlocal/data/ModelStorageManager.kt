package com.nikolaspaci.app.llamallmlocal.data

import android.content.Context
import java.io.File

class ModelStorageManager(private val context: Context) {

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

    fun hasVisionAdapter(modelPath: String): Boolean {
        val parentDir = File(modelPath).parentFile ?: return false
        return parentDir.listFiles()?.any { file ->
            file.isFile &&
            file.name.contains("mmproj", ignoreCase = true) &&
            file.name.endsWith(".gguf", ignoreCase = true)
        } == true
    }
}

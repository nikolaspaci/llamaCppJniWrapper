package com.nikolaspaci.app.llamallmlocal.data.repository

import com.nikolaspaci.app.llamallmlocal.data.ModelStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CachedModelEntry(
    val file: File,
    val sizeBytes: Long,
    val isMmproj: Boolean,
    val parentDirName: String
) {
    val absolutePath: String get() = file.absolutePath
}

data class DeleteResult(
    val deletedCount: Int,
    val freedBytes: Long,
    val failures: List<String>
)

@Singleton
class ModelCacheRepository @Inject constructor(
    private val storage: ModelStorageManager,
    private val modelRepository: ModelRepository
) {

    suspend fun list(): List<CachedModelEntry> = withContext(Dispatchers.IO) {
        storage.listAllFilesIncludingMmproj()
            .map { f ->
                CachedModelEntry(
                    file = f,
                    sizeBytes = f.length(),
                    isMmproj = f.name.contains("mmproj", ignoreCase = true),
                    parentDirName = f.parentFile?.name.orEmpty()
                )
            }
            .sortedByDescending { it.sizeBytes }
    }

    suspend fun delete(paths: Set<String>): DeleteResult = withContext(Dispatchers.IO) {
        var deletedCount = 0
        var freedBytes = 0L
        val failures = mutableListOf<String>()
        for (path in paths) {
            val file = File(path)
            val size = if (file.exists()) file.length() else 0L
            val ok = try {
                storage.deleteModel(file)
            } catch (_: Exception) {
                false
            }
            if (ok) {
                deletedCount++
                freedBytes += size
                runCatching { modelRepository.deleteByFilePath(path) }
            } else {
                failures += path
            }
        }
        DeleteResult(deletedCount, freedBytes, failures)
    }
}

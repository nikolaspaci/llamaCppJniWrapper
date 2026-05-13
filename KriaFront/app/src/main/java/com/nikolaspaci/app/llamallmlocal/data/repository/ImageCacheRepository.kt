package com.nikolaspaci.app.llamallmlocal.data.repository

import com.nikolaspaci.app.llamallmlocal.data.DeleteImagesResult
import com.nikolaspaci.app.llamallmlocal.data.ImageStorageManager
import com.nikolaspaci.app.llamallmlocal.data.database.ChatDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCacheRepository @Inject constructor(
    private val imageStorageManager: ImageStorageManager,
    private val chatDao: ChatDao
) {

    data class Stats(val totalBytes: Long, val count: Int)

    suspend fun stats(): Stats = Stats(
        totalBytes = imageStorageManager.totalSizeBytes(),
        count = imageStorageManager.countImages()
    )

    suspend fun clearAll(): DeleteImagesResult {
        val result = imageStorageManager.deleteAllImages()
        chatDao.clearAllMediaReferences()
        return result
    }
}

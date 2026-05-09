package com.nikolaspaci.app.llamallmlocal.data.huggingface

import android.content.Context
import com.nikolaspaci.app.llamallmlocal.data.ModelStorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long, val progress: Float) : DownloadState()
    data class Completed(val filePath: String) : DownloadState()
    data class Failed(val error: String) : DownloadState()
    object Cancelled : DownloadState()
}

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val apiClient: HuggingFaceApiClient
) {
    private val storageManager = ModelStorageManager(context)

    companion object {
        private const val PROGRESS_THROTTLE_BYTES = 256 * 1024L // 256KB
    }

    fun downloadFile(repoId: String, filename: String): Flow<DownloadState> = flow {
        emit(DownloadState.Idle)

        val url = apiClient.getDownloadUrl(repoId, filename)
        val request = Request.Builder().url(url).build()

        val modelDir = storageManager.modelDir(filename)
        val tempFile = File(modelDir, "$filename.download")
        val targetFile = storageManager.modelFile(filename)

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(DownloadState.Failed("Download failed: HTTP ${response.code}"))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadState.Failed("Empty response body"))
                return@flow
            }

            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L
            var lastEmittedBytes = 0L

            emit(DownloadState.Downloading(0, totalBytes, 0f))

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        if (bytesDownloaded - lastEmittedBytes >= PROGRESS_THROTTLE_BYTES) {
                            val progress = if (totalBytes > 0) {
                                bytesDownloaded.toFloat() / totalBytes
                            } else 0f
                            emit(DownloadState.Downloading(bytesDownloaded, totalBytes, progress))
                            lastEmittedBytes = bytesDownloaded
                        }
                    }
                }
            }

            tempFile.renameTo(targetFile)
            emit(DownloadState.Completed(targetFile.absolutePath))
        } catch (e: kotlinx.coroutines.CancellationException) {
            tempFile.delete()
            emit(DownloadState.Cancelled)
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            emit(DownloadState.Failed(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)
}

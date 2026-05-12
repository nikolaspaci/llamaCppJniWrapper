package com.nikolaspaci.app.llamallmlocal.data.huggingface

import com.nikolaspaci.app.llamallmlocal.data.WhisperModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

enum class WhisperModelVariant(val fileName: String, val approxBytes: Long) {
    TINY("ggml-tiny.bin", 77_700_000L),
    BASE("ggml-base.bin", 148_000_000L),
    SMALL("ggml-small.bin", 488_000_000L);

    companion object {
        val DEFAULT = BASE
        const val HF_REPO = "ggerganov/whisper.cpp"
        fun url(variant: WhisperModelVariant): String =
            "https://huggingface.co/$HF_REPO/resolve/main/${variant.fileName}"
    }
}

@Singleton
class WhisperModelDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val storage: WhisperModelManager
) {

    companion object {
        private const val PROGRESS_THROTTLE_BYTES = 512 * 1024L
    }

    fun download(variant: WhisperModelVariant = WhisperModelVariant.DEFAULT): Flow<DownloadState> = flow {
        emit(DownloadState.Idle)

        val target = storage.whisperModelFile(variant.fileName)
        if (target.exists() && target.length() > 0L) {
            emit(DownloadState.Completed(target.absolutePath))
            return@flow
        }

        val temp = storage.whisperModelFile("${variant.fileName}.download")
        val request = Request.Builder().url(WhisperModelVariant.url(variant)).build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(DownloadState.Failed("HTTP ${response.code}"))
                return@flow
            }
            val body = response.body ?: run {
                emit(DownloadState.Failed("Empty response body"))
                return@flow
            }

            val totalBytes = body.contentLength().takeIf { it > 0 } ?: variant.approxBytes
            var bytesDownloaded = 0L
            var lastEmitted = 0L

            emit(DownloadState.Downloading(0, totalBytes, 0f))

            body.byteStream().use { input ->
                temp.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buf, 0, read)
                        bytesDownloaded += read
                        if (bytesDownloaded - lastEmitted >= PROGRESS_THROTTLE_BYTES) {
                            val progress = (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                            emit(DownloadState.Downloading(bytesDownloaded, totalBytes, progress))
                            lastEmitted = bytesDownloaded
                        }
                    }
                }
            }

            if (!temp.renameTo(target)) {
                temp.delete()
                emit(DownloadState.Failed("Impossible de finaliser le fichier"))
                return@flow
            }
            emit(DownloadState.Completed(target.absolutePath))
        } catch (e: kotlinx.coroutines.CancellationException) {
            temp.delete()
            emit(DownloadState.Cancelled)
            throw e
        } catch (e: Exception) {
            temp.delete()
            emit(DownloadState.Failed(e.message ?: "Erreur inconnue"))
        }
    }.flowOn(Dispatchers.IO)
}

package com.nikolaspaci.app.llamallmlocal.engine

import android.content.Context
import android.util.Log
import com.nikolaspaci.app.llamallmlocal.TranscribeCallback
import com.nikolaspaci.app.llamallmlocal.WhisperApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechEngine {

    private var sessionPtr: Long = 0
    private var currentModelPath: String? = null
    private val mutex = Mutex()

    private val _loadState = MutableStateFlow<SpeechEngine.LoadState>(SpeechEngine.LoadState.Idle)
    override val loadState: StateFlow<SpeechEngine.LoadState> = _loadState.asStateFlow()

    override suspend fun loadModel(modelPath: String, nThreads: Int, useGpu: Boolean): Result<Unit> {
        return mutex.withLock {
            try {
                val modelName = File(modelPath).nameWithoutExtension
                if (sessionPtr != 0L && currentModelPath == modelPath) {
                    _loadState.value = SpeechEngine.LoadState.Loaded(modelName)
                    return@withLock Result.success(Unit)
                }

                _loadState.value = SpeechEngine.LoadState.Loading(modelName)

                if (sessionPtr != 0L) {
                    withContext(Dispatchers.IO) { WhisperApi.free(sessionPtr) }
                    sessionPtr = 0
                    currentModelPath = null
                }

                withContext(Dispatchers.IO) {
                    sessionPtr = WhisperApi.init(modelPath, nThreads, useGpu)
                }

                if (sessionPtr == 0L) {
                    val msg = "Echec du chargement du modele whisper"
                    _loadState.value = SpeechEngine.LoadState.Error(msg)
                    Result.failure(IllegalStateException(msg))
                } else {
                    currentModelPath = modelPath
                    _loadState.value = SpeechEngine.LoadState.Loaded(modelName)
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadModel failed", e)
                _loadState.value = SpeechEngine.LoadState.Error(e.message ?: "Erreur inconnue")
                Result.failure(e)
            }
        }
    }

    override suspend fun unloadModel(): Result<Unit> {
        return mutex.withLock {
            try {
                if (sessionPtr != 0L) {
                    withContext(Dispatchers.IO) { WhisperApi.free(sessionPtr) }
                    sessionPtr = 0
                    currentModelPath = null
                }
                _loadState.value = SpeechEngine.LoadState.Idle
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun transcribe(pcm: FloatArray, language: String, translate: Boolean): Flow<TranscriptionEvent> = callbackFlow {
        if (sessionPtr == 0L) {
            trySend(TranscriptionEvent.Error("Aucun modele whisper charge"))
            close()
            return@callbackFlow
        }

        val callback = object : TranscribeCallback {
            override fun onSegment(text: String, startMs: Long, endMs: Long) {
                trySend(TranscriptionEvent.Segment(text, startMs, endMs))
            }

            override fun onComplete(fullText: String, durationSec: Double) {
                trySend(TranscriptionEvent.Completed(fullText, durationSec))
                close()
            }

            override fun onError(error: String) {
                trySend(TranscriptionEvent.Error(error))
                close()
            }
        }

        WhisperApi.transcribePcm(sessionPtr, pcm, language, translate, callback)

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    override fun stop() {
        if (sessionPtr != 0L) {
            WhisperApi.stopTranscribe(sessionPtr)
        }
    }

    override fun isLoaded(): Boolean = sessionPtr != 0L

    override fun getCurrentModelPath(): String? = currentModelPath

    companion object {
        private const val TAG = "WhisperEngine"
    }
}

package com.nikolaspaci.app.llamallmlocal.engine

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nikolaspaci.app.llamallmlocal.LlamaApi
import com.nikolaspaci.app.llamallmlocal.PredictCallback
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.jni.PredictionEvent
import com.nikolaspaci.app.llamallmlocal.util.RemoteErrorLogger
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
class LlamaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelLoadGuard: ModelLoadGuard,
    private val errorLogger: RemoteErrorLogger
) : ModelEngine {

    private var sessionPtr: Long = 0
    private var currentModelPath: String? = null
    private var currentSystemPrompt: String = ""
    private var currentLoadParams: LoadTimeParams? = null
    private val mutex = Mutex()
    private var backendsLoaded = false

    private data class LoadTimeParams(
        val contextSize: Int,
        val threadCount: Int,
        val useGpu: Boolean,
        val gpuLayers: Int
    ) {
        companion object {
            fun from(p: ModelParameter) = LoadTimeParams(
                contextSize = p.contextSize,
                threadCount = p.threadCount,
                useGpu = p.useGpu,
                gpuLayers = p.gpuLayers
            )
        }
    }

    private fun ensureBackendsLoaded() {
        if (!backendsLoaded) {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            Log.i(TAG, "Loading GGML backends from: $nativeLibDir")
            LlamaApi.loadBackends(nativeLibDir)
            backendsLoaded = true
        }
    }

    private val _loadState = MutableStateFlow<ModelEngine.LoadState>(ModelEngine.LoadState.Idle)
    override val loadState: StateFlow<ModelEngine.LoadState> = _loadState.asStateFlow()

    override suspend fun loadModel(modelPath: String, parameters: ModelParameter): Result<Unit> {
        val modelFileName = File(modelPath).name
        val modelSizeBytes = runCatching { File(modelPath).length() }.getOrDefault(-1L)
        errorLogger.checkpoint("loadModel.start", mapOf(
            "modelFile" to modelFileName,
            "modelSizeBytes" to modelSizeBytes,
            "contextSize" to parameters.contextSize,
            "threadCount" to parameters.threadCount,
            "useGpu" to parameters.useGpu,
            "gpuLayers" to parameters.gpuLayers
        ))
        return mutex.withLock {
            try {
                val modelName = File(modelPath).nameWithoutExtension
                _loadState.value = ModelEngine.LoadState.Loading(0f, modelName)

                // Pre-flight check
                val preflight = modelLoadGuard.check(modelPath, parameters)
                errorLogger.checkpoint("loadModel.preflight", mapOf(
                    "canLoad" to preflight.canLoad,
                    "error" to preflight.error,
                    "warnings" to preflight.warnings.joinToString("; ").take(500),
                    "adjusted" to (preflight.adjustedParameters != null)
                ))
                if (!preflight.canLoad) {
                    val error = preflight.error ?: "Pre-flight check failed"
                    _loadState.value = ModelEngine.LoadState.Error(error)
                    errorLogger.log("LlamaEngine.loadModel.preflight",
                        IllegalStateException(error),
                        mapOf("modelFile" to modelFileName))
                    return@withLock Result.failure(IllegalStateException(error))
                }
                for (warning in preflight.warnings) {
                    Log.w(TAG, "ModelLoadGuard: $warning")
                }
                val effectiveParams = preflight.adjustedParameters ?: parameters
                val newLoadParams = LoadTimeParams.from(effectiveParams)

                if (sessionPtr != 0L &&
                    currentModelPath == modelPath &&
                    currentLoadParams == newLoadParams) {
                    Log.i(TAG, "Reusing already-loaded model: $modelName")
                    currentSystemPrompt = effectiveParams.systemPrompt
                    _loadState.value = ModelEngine.LoadState.Loaded(modelName)
                    errorLogger.checkpoint("loadModel.reused", mapOf("modelFile" to modelFileName))
                    return@withLock Result.success(Unit)
                }

                if (sessionPtr != 0L) {
                    errorLogger.checkpoint("loadModel.freeingPrevious")
                    withContext(Dispatchers.IO) {
                        LlamaApi.free(sessionPtr)
                    }
                    sessionPtr = 0
                    currentModelPath = null
                    currentLoadParams = null
                }

                withContext(Dispatchers.IO) {
                    errorLogger.checkpoint("loadModel.ensureBackends.start",
                        mapOf("backendsAlreadyLoaded" to backendsLoaded))
                    ensureBackendsLoaded()
                    errorLogger.checkpoint("loadModel.ensureBackends.done")

                    errorLogger.checkpoint("loadModel.llamaInit.start", mapOf(
                        "modelFile" to modelFileName,
                        "contextSize" to effectiveParams.contextSize,
                        "threadCount" to effectiveParams.threadCount
                    ))
                    sessionPtr = LlamaApi.init(modelPath, effectiveParams)
                    errorLogger.checkpoint("loadModel.llamaInit.done",
                        mapOf("sessionOk" to (sessionPtr != 0L)))
                }

                if (sessionPtr == 0L) {
                    _loadState.value = ModelEngine.LoadState.Error("Echec du chargement du modele")
                    errorLogger.log("LlamaEngine.loadModel.llamaInit",
                        IllegalStateException("LlamaApi.init returned 0"),
                        mapOf("modelFile" to modelFileName))
                    Result.failure(IllegalStateException("Model loading failed"))
                } else {
                    currentModelPath = modelPath
                    currentLoadParams = newLoadParams
                    currentSystemPrompt = effectiveParams.systemPrompt

                    // Auto-detect and load multimodal mmproj file
                    tryInitMultimodal(modelPath)

                    _loadState.value = ModelEngine.LoadState.Loaded(modelName)
                    setCrashlyticsModelKeys(modelName, effectiveParams)
                    errorLogger.checkpoint("loadModel.success", mapOf("modelFile" to modelFileName))
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                _loadState.value = ModelEngine.LoadState.Error(e.message ?: "Erreur inconnue")
                errorLogger.log("LlamaEngine.loadModel",
                    e,
                    mapOf("modelFile" to modelFileName, "modelSizeBytes" to modelSizeBytes))
                Result.failure(e)
            }
        }
    }

    private suspend fun tryInitMultimodal(modelPath: String) {
        withContext(Dispatchers.IO) {
            try {
                val modelFile = File(modelPath)
                val modelDir = modelFile.parentFile ?: return@withContext
                val modelBaseName = modelFile.nameWithoutExtension

                // Look for mmproj file in the same directory
                val mmprojFile = modelDir.listFiles()?.firstOrNull { file ->
                    file.name.contains("mmproj", ignoreCase = true) &&
                    file.extension.equals("gguf", ignoreCase = true)
                }

                if (mmprojFile != null) {
                    Log.i(TAG, "Found mmproj file: ${mmprojFile.absolutePath}")
                    val success = LlamaApi.initMultimodal(sessionPtr, mmprojFile.absolutePath)
                    if (success) {
                        Log.i(TAG, "Multimodal initialized successfully, hasVision=${LlamaApi.hasVision(sessionPtr)}")
                    } else {
                        Log.w(TAG, "Failed to initialize multimodal with mmproj: ${mmprojFile.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error during multimodal init", e)
            }
        }
    }

    private fun setCrashlyticsModelKeys(modelName: String, params: ModelParameter) {
        try {
            val crashlytics = Firebase.crashlytics
            crashlytics.setCustomKey("model_name", modelName)
            crashlytics.setCustomKey("context_size", params.contextSize)
            crashlytics.setCustomKey("thread_count", params.threadCount)
            crashlytics.setCustomKey("use_gpu", params.useGpu)
            crashlytics.setCustomKey("gpu_layers", params.gpuLayers)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set Crashlytics model keys", e)
        }
    }

    companion object {
        private const val TAG = "LlamaEngine"
    }

    override suspend fun unloadModel(): Result<Unit> {
        return mutex.withLock {
            try {
                if (sessionPtr != 0L) {
                    withContext(Dispatchers.IO) {
                        LlamaApi.free(sessionPtr)
                    }
                    sessionPtr = 0
                    currentModelPath = null
                    currentLoadParams = null
                }
                _loadState.value = ModelEngine.LoadState.Idle
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun stopPredict() {
        if (sessionPtr != 0L) {
            LlamaApi.stopPredict(sessionPtr)
        }
    }

    override fun predict(prompt: String, parameters: ModelParameter, enableThinking: Boolean): Flow<PredictionEvent> = callbackFlow {
        errorLogger.checkpoint("predict.start", mapOf(
            "promptLength" to prompt.length,
            "enableThinking" to enableThinking,
            "sessionOk" to (sessionPtr != 0L)
        ))
        if (sessionPtr == 0L) {
            errorLogger.log("LlamaEngine.predict",
                IllegalStateException("Aucun modele charge"),
                mapOf("promptLength" to prompt.length))
            trySend(PredictionEvent.Error("Aucun modele charge", isRecoverable = false))
            close()
            return@callbackFlow
        }

        var firstTokenSent = false
        val callback = object : PredictCallback {
            override fun onToken(token: String) {
                if (!firstTokenSent) {
                    firstTokenSent = true
                    errorLogger.checkpoint("predict.firstToken")
                }
                trySend(PredictionEvent.Token(token))
            }

            override fun onThinkingToken(token: String) {
                if (!firstTokenSent) {
                    firstTokenSent = true
                    errorLogger.checkpoint("predict.firstToken", mapOf("thinking" to true))
                }
                trySend(PredictionEvent.ThinkingToken(token))
            }

            override fun onComplete(tokensPerSecond: Double, durationInSeconds: Long) {
                errorLogger.checkpoint("predict.complete", mapOf(
                    "tokensPerSecond" to tokensPerSecond,
                    "durationInSeconds" to durationInSeconds
                ))
                trySend(PredictionEvent.Completion(tokensPerSecond, durationInSeconds))
                close()
            }

            override fun onError(error: String) {
                errorLogger.log("LlamaEngine.predict.callback",
                    RuntimeException("Prediction error: $error"),
                    mapOf("promptLength" to prompt.length))
                trySend(PredictionEvent.Error(error, isRecoverable = true))
                close()
            }
        }

        LlamaApi.predict(sessionPtr, prompt, parameters, enableThinking, callback)

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    override fun predictWithMedia(prompt: String, imageData: ByteArray?, parameters: ModelParameter, enableThinking: Boolean): Flow<PredictionEvent> = callbackFlow {
        errorLogger.checkpoint("predictWithMedia.start", mapOf(
            "promptLength" to prompt.length,
            "hasImage" to (imageData != null),
            "imageBytes" to (imageData?.size ?: 0),
            "enableThinking" to enableThinking,
            "sessionOk" to (sessionPtr != 0L)
        ))
        if (sessionPtr == 0L) {
            errorLogger.log("LlamaEngine.predictWithMedia",
                IllegalStateException("Aucun modele charge"),
                mapOf("promptLength" to prompt.length))
            trySend(PredictionEvent.Error("Aucun modele charge", isRecoverable = false))
            close()
            return@callbackFlow
        }

        var firstTokenSent = false
        val callback = object : PredictCallback {
            override fun onToken(token: String) {
                if (!firstTokenSent) {
                    firstTokenSent = true
                    errorLogger.checkpoint("predictWithMedia.firstToken")
                }
                trySend(PredictionEvent.Token(token))
            }

            override fun onThinkingToken(token: String) {
                if (!firstTokenSent) {
                    firstTokenSent = true
                    errorLogger.checkpoint("predictWithMedia.firstToken", mapOf("thinking" to true))
                }
                trySend(PredictionEvent.ThinkingToken(token))
            }

            override fun onComplete(tokensPerSecond: Double, durationInSeconds: Long) {
                errorLogger.checkpoint("predictWithMedia.complete", mapOf(
                    "tokensPerSecond" to tokensPerSecond,
                    "durationInSeconds" to durationInSeconds
                ))
                trySend(PredictionEvent.Completion(tokensPerSecond, durationInSeconds))
                close()
            }

            override fun onError(error: String) {
                errorLogger.log("LlamaEngine.predictWithMedia.callback",
                    RuntimeException("Prediction error: $error"),
                    mapOf("promptLength" to prompt.length))
                trySend(PredictionEvent.Error(error, isRecoverable = true))
                close()
            }
        }

        LlamaApi.predictWithMedia(sessionPtr, prompt, imageData, parameters, enableThinking, callback)

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    override suspend fun restoreHistory(messages: List<ChatMessage>, systemPrompt: String) {
        if (sessionPtr == 0L) return

        val prompt = systemPrompt.ifEmpty { currentSystemPrompt }
        withContext(Dispatchers.IO) {
            LlamaApi.restoreHistory(sessionPtr, messages.toTypedArray(), prompt)
        }
    }

    override fun isModelLoaded(): Boolean = sessionPtr != 0L

    override fun getCurrentModelPath(): String? = currentModelPath

    override fun supportsThinking(): Boolean {
        return sessionPtr != 0L && LlamaApi.supportsThinking(sessionPtr)
    }

    override fun hasVision(): Boolean {
        return sessionPtr != 0L && LlamaApi.hasVision(sessionPtr)
    }
}

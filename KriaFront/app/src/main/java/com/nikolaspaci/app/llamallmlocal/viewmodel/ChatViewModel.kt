package com.nikolaspaci.app.llamallmlocal.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.Sender
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository
import com.nikolaspaci.app.llamallmlocal.engine.ModelEngine
import com.nikolaspaci.app.llamallmlocal.engine.ModelParameterProvider
import com.nikolaspaci.app.llamallmlocal.jni.PredictionEvent
import com.nikolaspaci.app.llamallmlocal.usecase.PredictUseCase
import com.nikolaspaci.app.llamallmlocal.util.RemoteErrorLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class Stats(
    val tokensPerSecond: Double,
    val durationInSeconds: Long,
    val totalTokens: Int = 0
)

sealed class ChatErrorType {
    object ModelUnavailable : ChatErrorType()
    object PredictionFailed : ChatErrorType()
    data class Generic(val message: String) : ChatErrorType()
}

sealed class ChatUiState {
    object Idle : ChatUiState()

    data class ModelLoading(
        val progress: Float = 0f,
        val modelName: String = ""
    ) : ChatUiState()

    data class Ready(
        val messages: List<ChatMessage>,
        val modelName: String
    ) : ChatUiState()

    data class Generating(
        val messages: List<ChatMessage>,
        val currentResponse: String,
        val currentThinking: String,
        val isThinking: Boolean,
        val tokensGenerated: Int,
        val modelName: String
    ) : ChatUiState()

    data class MessageComplete(
        val messages: List<ChatMessage>,
        val stats: Stats,
        val modelName: String
    ) : ChatUiState()

    data class Error(
        val errorType: ChatErrorType,
        val previousMessages: List<ChatMessage>? = null,
        val canRetry: Boolean = true
    ) : ChatUiState()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val engine: ModelEngine,
    private val predictUseCase: PredictUseCase,
    private val parameterProvider: ModelParameterProvider,
    private val errorLogger: RemoteErrorLogger,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversationId: Long = savedStateHandle.get<Long>("conversationId") ?: 0L

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var predictionJob: Job? = null
    private var currentMessages: List<ChatMessage> = emptyList()
    private var accumulatedResponse = StringBuilder()
    private var accumulatedThinking = StringBuilder()
    private var isThinking = false
    private var accumulatedTokenCount = 0
    private val _modelPath = MutableStateFlow<String?>(null)
    val currentModelPath: StateFlow<String?> = _modelPath.asStateFlow()

    // Capabilities
    private val _supportsThinking = MutableStateFlow(false)
    val supportsThinking: StateFlow<Boolean> = _supportsThinking.asStateFlow()

    private val _hasVision = MutableStateFlow(false)
    val hasVision: StateFlow<Boolean> = _hasVision.asStateFlow()

    // Pending image attachment
    private val _pendingImageUri = MutableStateFlow<Uri?>(null)
    val pendingImageUri: StateFlow<Uri?> = _pendingImageUri.asStateFlow()

    private var pendingImageData: ByteArray? = null

    init {
        observeConversation()
        observeModelState()
    }

    private fun observeConversation() {
        viewModelScope.launch {
            chatRepository.getConversation(conversationId)
                .distinctUntilChanged()
                .collect { conversationWithMessages ->
                    conversationWithMessages?.let { cwm ->
                        currentMessages = cwm.messages

                        if (_modelPath.value != cwm.conversation.modelPath) {
                            _modelPath.value = cwm.conversation.modelPath
                            loadModel(cwm.conversation.modelPath)
                        }

                        updateUiState()
                    }
                }
        }
    }

    private fun observeModelState() {
        viewModelScope.launch {
            engine.loadState.collect { state ->
                when (state) {
                    is ModelEngine.LoadState.Loading -> {
                        _uiState.value = ChatUiState.ModelLoading(
                            progress = state.progress,
                            modelName = state.modelName
                        )
                    }
                    is ModelEngine.LoadState.Loaded -> {
                        _supportsThinking.value = engine.supportsThinking()
                        _hasVision.value = engine.hasVision()
                        updateUiState()
                    }
                    is ModelEngine.LoadState.Error -> {
                        errorLogger.checkpoint("ChatVM.LoadState.Error", mapOf(
                            "conversationId" to conversationId,
                            "modelPath" to (_modelPath.value ?: ""),
                            "message" to state.message
                        ))
                        _uiState.value = ChatUiState.Error(
                            errorType = ChatErrorType.Generic(state.message),
                            previousMessages = currentMessages,
                            canRetry = true
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun loadModel(path: String) {
        errorLogger.checkpoint("ChatVM.loadModel.start", mapOf(
            "conversationId" to conversationId,
            "modelPath" to path,
            "fileExists" to (path.isNotBlank() && File(path).exists())
        ))
        if (path.isBlank() || !File(path).exists()) {
            errorLogger.log("ChatVM.loadModel.missingFile",
                IllegalStateException("Model file missing or path blank"),
                mapOf("path" to path))
            _uiState.value = ChatUiState.Error(
                errorType = ChatErrorType.ModelUnavailable,
                previousMessages = currentMessages,
                canRetry = false
            )
            return
        }
        val parameters = parameterProvider.getParametersForConversation(conversationId, path)
        val result = engine.loadModel(path, parameters)
        if (result.isSuccess) {
            errorLogger.checkpoint("ChatVM.loadModel.engineSuccess")
            engine.restoreHistory(currentMessages)
            errorLogger.checkpoint("ChatVM.restoreHistory.done",
                mapOf("messageCount" to currentMessages.size))
            _supportsThinking.value = engine.supportsThinking()
            _hasVision.value = engine.hasVision()
            triggerPendingPredictionIfNeeded()
        } else {
            errorLogger.checkpoint("ChatVM.loadModel.engineFailure",
                mapOf("error" to (result.exceptionOrNull()?.message ?: "unknown")))
        }
    }

    private fun triggerPendingPredictionIfNeeded() {
        val lastMessage = currentMessages.lastOrNull() ?: return
        if (lastMessage.sender == Sender.USER) {
            startPrediction(lastMessage.message)
        }
    }

    private fun updateUiState() {
        if (_uiState.value !is ChatUiState.Generating) {
            _uiState.value = ChatUiState.Ready(
                messages = currentMessages,
                modelName = getModelName()
            )
        }
    }

    fun attachImage(uri: Uri, imageBytes: ByteArray) {
        _pendingImageUri.value = uri
        pendingImageData = imageBytes
    }

    fun removeAttachment() {
        _pendingImageUri.value = null
        pendingImageData = null
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val imageData = pendingImageData
        val imageUri = _pendingImageUri.value

        // Clear attachment
        _pendingImageUri.value = null
        pendingImageData = null

        viewModelScope.launch {
            val userMessage = ChatMessage(
                conversationId = conversationId,
                sender = Sender.USER,
                message = text.trim(),
                mediaPath = imageUri?.toString(),
                mediaType = if (imageUri != null) "image" else null
            )

            // Optimistically add user message so it appears immediately
            currentMessages = currentMessages + userMessage
            _uiState.value = ChatUiState.Ready(
                messages = currentMessages,
                modelName = getModelName()
            )

            chatRepository.addMessageToConversation(userMessage)

            startPrediction(text.trim(), imageData)
        }
    }

    private fun startPrediction(prompt: String, imageData: ByteArray? = null) {
        engine.stopPredict()
        predictionJob?.cancel()

        predictionJob = viewModelScope.launch {
            accumulatedResponse.clear()
            accumulatedThinking.clear()
            isThinking = false
            accumulatedTokenCount = 0

            _uiState.value = ChatUiState.Generating(
                messages = currentMessages,
                currentResponse = "",
                currentThinking = "",
                isThinking = false,
                tokensGenerated = 0,
                modelName = getModelName()
            )

            predictUseCase(prompt, _modelPath.value ?: "", conversationId, imageData)
                .catch { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    errorLogger.log("ChatVM.startPrediction",
                        e,
                        mapOf("conversationId" to conversationId, "promptLength" to prompt.length))
                    val msg = e.message
                    _uiState.value = ChatUiState.Error(
                        errorType = if (msg.isNullOrBlank()) ChatErrorType.PredictionFailed else ChatErrorType.Generic(msg),
                        previousMessages = currentMessages,
                        canRetry = true
                    )
                }
                .collect { event ->
                    when (event) {
                        is PredictionEvent.Token -> {
                            accumulatedResponse.append(event.value)
                            accumulatedTokenCount++
                            isThinking = false

                            _uiState.value = ChatUiState.Generating(
                                messages = currentMessages,
                                currentResponse = accumulatedResponse.toString(),
                                currentThinking = accumulatedThinking.toString(),
                                isThinking = false,
                                tokensGenerated = accumulatedTokenCount,
                                modelName = getModelName()
                            )
                        }

                        is PredictionEvent.ThinkingToken -> {
                            accumulatedThinking.append(event.value)
                            accumulatedTokenCount++
                            isThinking = true

                            _uiState.value = ChatUiState.Generating(
                                messages = currentMessages,
                                currentResponse = accumulatedResponse.toString(),
                                currentThinking = accumulatedThinking.toString(),
                                isThinking = true,
                                tokensGenerated = accumulatedTokenCount,
                                modelName = getModelName()
                            )
                        }

                        is PredictionEvent.Completion -> {
                            val botMessage = ChatMessage(
                                conversationId = conversationId,
                                sender = Sender.BOT,
                                message = accumulatedResponse.toString(),
                                thinkingContent = accumulatedThinking.toString()
                            )
                            chatRepository.addMessageToConversation(botMessage)

                            _uiState.value = ChatUiState.MessageComplete(
                                messages = currentMessages + botMessage,
                                stats = Stats(
                                    tokensPerSecond = event.tokensPerSecond,
                                    durationInSeconds = event.durationInSeconds,
                                    totalTokens = accumulatedTokenCount
                                ),
                                modelName = getModelName()
                            )
                        }

                        is PredictionEvent.Error -> {
                            _uiState.value = ChatUiState.Error(
                                errorType = ChatErrorType.Generic(event.message),
                                previousMessages = currentMessages,
                                canRetry = event.isRecoverable
                            )
                        }

                        else -> {}
                    }
                }
        }
    }

    fun cancelPrediction() {
        engine.stopPredict()
        predictionJob?.cancel()
        predictionJob = null

        val partialResponse = accumulatedResponse.toString()
        val partialThinking = accumulatedThinking.toString()
        if (partialResponse.isNotEmpty() || partialThinking.isNotEmpty()) {
            viewModelScope.launch {
                val botMessage = ChatMessage(
                    conversationId = conversationId,
                    sender = Sender.BOT,
                    message = partialResponse,
                    thinkingContent = partialThinking
                )
                chatRepository.addMessageToConversation(botMessage)
            }
        }

        _uiState.value = ChatUiState.Ready(
            messages = currentMessages,
            modelName = getModelName()
        )
    }

    fun retry() {
        currentMessages.lastOrNull { it.sender == Sender.USER }?.let { lastUserMessage ->
            startPrediction(lastUserMessage.message)
        }
    }

    fun changeModel(newModelPath: String) {
        viewModelScope.launch {
            chatRepository.updateConversationModel(conversationId, newModelPath)
            parameterProvider.ensureConversationParameters(conversationId, newModelPath)
        }
    }

    suspend fun deleteConversation() {
        chatRepository.getConversation(conversationId).firstOrNull()?.let {
            chatRepository.deleteConversation(it.conversation)
        }
    }

    private fun getModelName(): String {
        return _modelPath.value?.let { File(it).nameWithoutExtension } ?: ""
    }

    override fun onCleared() {
        super.onCleared()
        predictionJob?.cancel()
    }
}

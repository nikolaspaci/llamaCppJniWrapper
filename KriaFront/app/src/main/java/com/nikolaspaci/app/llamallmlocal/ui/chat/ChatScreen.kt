package com.nikolaspaci.app.llamallmlocal.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.ui.common.AdaptiveTopBar
import com.nikolaspaci.app.llamallmlocal.ui.common.SmartChatInput
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatErrorType
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatUiState
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onNavigateToSettings: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedModelPath by remember { mutableStateOf("") }
    val viewModelModelPath by viewModel.currentModelPath.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val hasVision by viewModel.hasVision.collectAsState()
    val pendingImageUri by viewModel.pendingImageUri.collectAsState()

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(it)
                )
                if (bitmap != null) {
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    bitmap.recycle()
                    viewModel.attachImage(it, stream.toByteArray())
                }
            } catch (e: Exception) {
                // Ignore errors reading image
            }
        }
    }

    LaunchedEffect(viewModelModelPath) {
        viewModelModelPath?.let { path ->
            if (path.isNotEmpty()) {
                selectedModelPath = path
            }
        }
    }

    val currentModelName = when (val state = uiState) {
        is ChatUiState.Ready -> state.modelName
        is ChatUiState.Generating -> state.modelName
        is ChatUiState.MessageComplete -> state.modelName
        is ChatUiState.ModelLoading -> state.modelName
        else -> ""
    }

    val displayModelName = if (currentModelName.isNotEmpty()) {
        File(currentModelName).nameWithoutExtension
    } else ""

    val isGenerating = uiState is ChatUiState.Generating
    val isModelReady = uiState is ChatUiState.Ready ||
                       uiState is ChatUiState.Generating ||
                       uiState is ChatUiState.MessageComplete

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            AdaptiveTopBar(
                modelName = displayModelName,
                onOpenDrawer = onOpenDrawer,
                onNewChat = onNewChat,
                onNavigateToSettings = {
                    onNavigateToSettings(selectedModelPath)
                }
            )
        },
        bottomBar = {
            SmartChatInput(
                onSendMessage = { viewModel.sendMessage(it) },
                isEnabled = isModelReady,
                isGenerating = isGenerating,
                onStopGeneration = { viewModel.cancelPrediction() },
                hasVision = hasVision,
                pendingImageUri = pendingImageUri,
                onAttachImage = { imagePickerLauncher.launch("image/*") },
                onRemoveAttachment = { viewModel.removeAttachment() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when (val state = uiState) {
            is ChatUiState.Idle -> {
                // Empty initial state
            }

            is ChatUiState.ModelLoading -> {
                ModelLoadingOverlay(
                    modelName = state.modelName,
                    progress = state.progress,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is ChatUiState.Ready -> {
                if (state.messages.isEmpty()) {
                    EmptyChatState(
                        modelName = currentModelName,
                        modifier = Modifier.padding(paddingValues)
                    )
                } else {
                    MessageList(
                        messages = state.messages,
                        streamingState = null,
                        lastMessageStats = null,
                        onCancelGeneration = {},
                        onCopyMessage = { text -> copyToClipboard(context, text) },
                        onRegenerateResponse = { viewModel.retry() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }

            is ChatUiState.Generating -> {
                MessageList(
                    messages = state.messages,
                    streamingState = StreamingState(
                        currentText = state.currentResponse,
                        tokensGenerated = state.tokensGenerated,
                        currentThinking = state.currentThinking,
                        isThinking = state.isThinking
                    ),
                    lastMessageStats = null,
                    onCancelGeneration = { viewModel.cancelPrediction() },
                    onCopyMessage = { text -> copyToClipboard(context, text) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is ChatUiState.MessageComplete -> {
                MessageList(
                    messages = state.messages,
                    streamingState = null,
                    lastMessageStats = state.stats,
                    onCancelGeneration = {},
                    onCopyMessage = { text -> copyToClipboard(context, text) },
                    onRegenerateResponse = { viewModel.retry() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is ChatUiState.Error -> {
                val errorMessage = when (val type = state.errorType) {
                    ChatErrorType.ModelUnavailable -> stringResource(R.string.chat_error_model_unavailable)
                    ChatErrorType.PredictionFailed -> stringResource(R.string.chat_error_prediction)
                    is ChatErrorType.Generic -> type.message
                }
                if (state.previousMessages != null && state.previousMessages.isNotEmpty()) {
                    // Show messages + snackbar for recoverable errors
                    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                        MessageList(
                            messages = state.previousMessages,
                            streamingState = null,
                            lastMessageStats = null,
                            onCancelGeneration = {},
                            onCopyMessage = { text -> copyToClipboard(context, text) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    LaunchedEffect(errorMessage) {
                        snackbarHostState.showSnackbar(errorMessage)
                    }
                } else {
                    ErrorScreen(
                        message = errorMessage,
                        onRetry = if (state.canRetry) {{ viewModel.retry() }} else null,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("message", text)
    clipboard.setPrimaryClip(clip)
}

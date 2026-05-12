package com.nikolaspaci.app.llamallmlocal.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.ui.common.AdaptiveTopBar
import com.nikolaspaci.app.llamallmlocal.ui.common.SmartChatInput
import com.nikolaspaci.app.llamallmlocal.ui.common.ModelSelector
import com.nikolaspaci.app.llamallmlocal.ui.common.VoiceInputUiState
import com.nikolaspaci.app.llamallmlocal.ui.common.WhisperModelPickerDialog
import com.nikolaspaci.app.llamallmlocal.viewmodel.HomeViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.SpeechInputViewModel
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeChatScreen(
    homeViewModel: HomeViewModel,
    modelFileViewModel: ModelFileViewModel,
    onStartChat: (Long) -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigateToManageModels: () -> Unit = {},
    onNavigateToSettings: ((String) -> Unit)? = null,
    updatedModelPath: String? = null,
    onUpdatedModelPathConsumed: () -> Unit = {}
) {
    var selectedModelPath by remember { mutableStateOf(modelFileViewModel.getModelPath() ?: "") }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }

    val hasVision = remember(selectedModelPath) {
        selectedModelPath.isNotEmpty() && modelFileViewModel.hasVisionAdapter(selectedModelPath)
    }

    LaunchedEffect(hasVision) {
        if (!hasVision) {
            pendingImageUri = null
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            pendingImageUri = it
        }
    }

    LaunchedEffect(updatedModelPath) {
        updatedModelPath?.let { path ->
            selectedModelPath = path
            modelFileViewModel.saveModelPath(path)
            onUpdatedModelPathConsumed()
        }
    }

    val displayModelName = if (selectedModelPath.isNotEmpty()) {
        File(selectedModelPath).nameWithoutExtension
    } else ""

    val speechViewModel: SpeechInputViewModel = hiltViewModel()
    val speechState by speechViewModel.state.collectAsState()
    val transcribedText by speechViewModel.transcribed.collectAsState()
    val showVariantPicker by speechViewModel.variantPickerVisible.collectAsState()

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) speechViewModel.startRecording()
    }

    val voiceUi: VoiceInputUiState = when (val s = speechState) {
        is SpeechInputViewModel.UiState.Recording -> VoiceInputUiState.Recording
        is SpeechInputViewModel.UiState.Transcribing -> VoiceInputUiState.Transcribing
        is SpeechInputViewModel.UiState.Downloading -> VoiceInputUiState.Downloading(s.progress)
        else -> VoiceInputUiState.Idle
    }

    LaunchedEffect(speechState) {
        val s = speechState
        if (s is SpeechInputViewModel.UiState.Error) {
            snackbarHostState.showSnackbar(s.message)
            speechViewModel.dismissError()
        }
    }

    if (showVariantPicker) {
        WhisperModelPickerDialog(
            onPick = { variant -> speechViewModel.chooseVariant(variant) },
            onDismiss = { speechViewModel.dismissVariantPicker() }
        )
    }

    Scaffold(
        topBar = {
            AdaptiveTopBar(
                modelName = displayModelName,
                onOpenDrawer = onOpenDrawer,
                onNavigateToSettings = if (selectedModelPath.isNotEmpty() && onNavigateToSettings != null) {
                    { onNavigateToSettings(selectedModelPath) }
                } else null
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SmartChatInput(
                onSendMessage = { userInput ->
                    if (selectedModelPath.isNotEmpty()) {
                        val imageUriToSend = pendingImageUri
                        pendingImageUri = null
                        scope.launch {
                            try {
                                val newConversationId = homeViewModel.startNewConversation(
                                    modelPath = selectedModelPath,
                                    firstMessage = userInput,
                                    imageUri = imageUriToSend
                                )
                                onStartChat(newConversationId)
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                Firebase.crashlytics.recordException(t)
                                snackbarHostState.showSnackbar(
                                    "Erreur: ${t::class.java.simpleName} - ${t.message ?: "(sans message)"}"
                                )
                            }
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Aucun modèle sélectionné")
                        }
                    }
                },
                isEnabled = selectedModelPath.isNotEmpty(),
                isGenerating = false,
                onStopGeneration = {},
                hasVision = hasVision,
                pendingImageUri = pendingImageUri,
                onAttachImage = { imagePickerLauncher.launch("image/*") },
                onRemoveAttachment = {
                    pendingImageUri = null
                },
                voiceState = voiceUi,
                onStartVoice = {
                    if (speechViewModel.hasMicrophonePermission()) {
                        speechViewModel.startRecording()
                    } else {
                        voicePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopVoice = { speechViewModel.stopRecording() },
                onCancelVoice = { speechViewModel.cancel() },
                injectedText = transcribedText,
                onInjectedTextConsumed = { speechViewModel.consumeTranscribed() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            ModelSelector(
                modelFileViewModel = modelFileViewModel,
                selectedModelPath = selectedModelPath,
                onModelSelected = {
                    selectedModelPath = it
                    modelFileViewModel.saveModelPath(it)
                },
                onNavigateToManageModels = onNavigateToManageModels,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

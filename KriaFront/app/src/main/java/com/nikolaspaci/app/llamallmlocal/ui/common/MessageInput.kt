package com.nikolaspaci.app.llamallmlocal.ui.common

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.nikolaspaci.app.llamallmlocal.R

private enum class InputState { EMPTY, HAS_TEXT, GENERATING, MIC }

enum class VoiceInputUiState { Unavailable, Idle, Recording, Transcribing }

@Composable
fun SmartChatInput(
    onSendMessage: (String) -> Unit,
    isEnabled: Boolean,
    isGenerating: Boolean,
    onStopGeneration: () -> Unit,
    hasVision: Boolean = false,
    pendingImageUri: Uri? = null,
    onAttachImage: () -> Unit = {},
    onRemoveAttachment: () -> Unit = {},
    voiceState: VoiceInputUiState = VoiceInputUiState.Unavailable,
    onStartVoice: () -> Unit = {},
    onStopVoice: () -> Unit = {},
    onCancelVoice: () -> Unit = {},
    injectedText: String? = null,
    onInjectedTextConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }

    LaunchedEffect(injectedText) {
        val incoming = injectedText
        if (!incoming.isNullOrBlank()) {
            text = if (text.isBlank()) incoming else "$text $incoming"
            onInjectedTextConsumed()
        }
    }

    val inputState = when {
        isGenerating -> InputState.GENERATING
        text.isNotBlank() -> InputState.HAS_TEXT
        voiceState != VoiceInputUiState.Unavailable -> InputState.MIC
        else -> InputState.EMPTY
    }

    Column(modifier = modifier) {
        // Image preview
        if (pendingImageUri != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .padding(bottom = 8.dp)
            ) {
                Box {
                    Image(
                        painter = rememberAsyncImagePainter(pendingImageUri),
                        contentDescription = stringResource(R.string.chat_attached_image),
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    // Remove button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .clickable { onRemoveAttachment() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.chat_remove_image),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            if (voiceState == VoiceInputUiState.Recording || voiceState == VoiceInputUiState.Transcribing) {
                VoiceRecordingBar(
                    voiceState = voiceState,
                    onValidate = onStopVoice,
                    onCancel = onCancelVoice
                )
                return@Surface
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Image attach button (only visible when model supports vision)
                if (hasVision && !isGenerating) {
                    IconButton(
                        onClick = onAttachImage,
                        enabled = isEnabled,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = stringResource(R.string.chat_attach_image),
                            modifier = Modifier.size(20.dp),
                            tint = if (pendingImageUri != null)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp)
                        .padding(vertical = 8.dp, horizontal = if (hasVision && !isGenerating) 0.dp else 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_message_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 5,
                        enabled = isEnabled,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }

                Crossfade(
                    targetState = inputState,
                    animationSpec = tween(200),
                    label = "input_action"
                ) { state ->
                    when (state) {
                        InputState.EMPTY -> {
                            IconButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = stringResource(R.string.chat_send),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        InputState.MIC -> {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                                onClick = { if (isEnabled) onStartVoice() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = stringResource(R.string.chat_voice_input),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        InputState.HAS_TEXT -> {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                                onClick = {
                                    if (text.isNotBlank() && isEnabled) {
                                        onSendMessage(text)
                                        text = ""
                                    }
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowUpward,
                                        contentDescription = stringResource(R.string.chat_send),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        InputState.GENERATING -> {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                                onClick = onStopGeneration
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = stringResource(R.string.chat_stop_generation),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Keep old API for backwards compatibility with HomeChatScreen
@Composable
fun MessageInput(
    onSendMessage: (String) -> Unit,
    isEnabled: Boolean
) {
    SmartChatInput(
        onSendMessage = onSendMessage,
        isEnabled = isEnabled,
        isGenerating = false,
        onStopGeneration = {}
    )
}

@Composable
private fun VoiceRecordingBar(
    voiceState: VoiceInputUiState,
    onValidate: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.chat_voice_cancel),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 36.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (voiceState == VoiceInputUiState.Transcribing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.chat_voice_transcribing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .padding(0.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = CircleShape,
                            modifier = Modifier.size(10.dp)
                        ) {}
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.chat_voice_recording),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Surface(
            shape = CircleShape,
            color = if (voiceState == VoiceInputUiState.Recording)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            modifier = Modifier.size(36.dp),
            onClick = { if (voiceState == VoiceInputUiState.Recording) onValidate() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.chat_voice_validate),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

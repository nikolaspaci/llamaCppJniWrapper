package com.nikolaspaci.app.llamallmlocal.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.data.huggingface.WhisperModelVariant
import com.nikolaspaci.app.llamallmlocal.ui.common.WhisperModelPickerDialog
import com.nikolaspaci.app.llamallmlocal.viewmodel.VoiceSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: VoiceSettingsViewModel = hiltViewModel()
) {
    val voice by viewModel.voiceSection.collectAsState()
    val pickerVisible by viewModel.pickerVisible.collectAsState()
    val deleteConfirmVisible by viewModel.deleteConfirmVisible.collectAsState()

    if (pickerVisible) {
        WhisperModelPickerDialog(
            onPick = { variant -> viewModel.chooseVariant(variant) },
            onDismiss = { viewModel.dismissPicker() },
            currentVariant = viewModel.currentVariant()
        )
    }

    if (deleteConfirmVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            title = { Text(stringResource(R.string.app_settings_voice_delete_confirm_title)) },
            text = { Text(stringResource(R.string.app_settings_voice_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDelete() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            VoiceInputSection(
                state = voice,
                onChange = { viewModel.requestChange() },
                onDelete = { viewModel.requestDelete() },
                onCancelDownload = { viewModel.cancelDownload() }
            )
        }
    }
}

@Composable
private fun VoiceInputSection(
    state: VoiceSettingsViewModel.VoiceSectionState,
    onChange: () -> Unit,
    onDelete: () -> Unit,
    onCancelDownload: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.MicNone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.app_settings_voice_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            when (state) {
                is VoiceSettingsViewModel.VoiceSectionState.Empty -> {
                    Text(
                        text = stringResource(R.string.app_settings_voice_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onChange,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.app_settings_voice_change))
                    }
                }
                is VoiceSettingsViewModel.VoiceSectionState.Installed -> {
                    Text(
                        text = stringResource(
                            R.string.app_settings_voice_installed,
                            variantLabel(state.variant),
                            formatBytes(state.sizeBytes)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onChange,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.app_settings_voice_change))
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(stringResource(R.string.app_settings_voice_delete))
                        }
                    }
                }
                is VoiceSettingsViewModel.VoiceSectionState.Downloading -> {
                    Text(
                        text = stringResource(
                            R.string.app_settings_voice_downloading,
                            variantLabel(state.variant),
                            (state.progress * 100).toInt()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
                is VoiceSettingsViewModel.VoiceSectionState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onChange,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun variantLabel(variant: WhisperModelVariant): String = stringResource(
    when (variant) {
        WhisperModelVariant.TINY -> R.string.app_settings_voice_variant_tiny
        WhisperModelVariant.BASE -> R.string.app_settings_voice_variant_base
        WhisperModelVariant.SMALL -> R.string.app_settings_voice_variant_small
    }
)

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0)
    else "%.0f MB".format(mb)
}

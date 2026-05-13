package com.nikolaspaci.app.llamallmlocal.ui.settings

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
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.viewmodel.ImagesSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImagesSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val confirmVisible by viewModel.confirmVisible.collectAsState()
    val lastEvent by viewModel.lastEvent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val toastTemplate = stringResource(R.string.images_settings_cleared_toast)

    LaunchedEffect(lastEvent) {
        val event = lastEvent
        if (event is ImagesSettingsViewModel.Event.Cleared) {
            snackbarHostState.showSnackbar(
                toastTemplate.format(event.deletedCount, formatBytesSize(event.freedBytes))
            )
            viewModel.consumeEvent()
        }
    }

    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClear() },
            title = { Text(stringResource(R.string.images_settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.images_settings_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmClear() }) {
                    Text(stringResource(R.string.images_settings_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClear() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.images_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.common_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ImageCacheSection(
                state = state,
                onClear = { viewModel.requestClear() }
            )
        }
    }
}

@Composable
private fun ImageCacheSection(
    state: ImagesSettingsViewModel.State,
    onClear: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.images_settings_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.images_settings_card_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (state) {
                is ImagesSettingsViewModel.State.Loading -> {
                    Text(
                        text = stringResource(R.string.images_settings_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ImagesSettingsViewModel.State.Idle -> {
                    if (state.count == 0) {
                        Text(
                            text = stringResource(R.string.images_settings_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.images_settings_size,
                                formatBytesSize(state.sizeBytes),
                                state.count
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onClear,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(stringResource(R.string.images_settings_clear))
                        }
                    }
                }
                is ImagesSettingsViewModel.State.Clearing -> {
                    Text(
                        text = stringResource(R.string.images_settings_clearing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is ImagesSettingsViewModel.State.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun formatBytesSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

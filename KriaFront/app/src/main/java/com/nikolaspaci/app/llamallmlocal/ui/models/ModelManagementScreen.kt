package com.nikolaspaci.app.llamallmlocal.ui.models

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    viewModel: ModelManagementViewModel,
    modelFileViewModel: ModelFileViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val activeModelPath by viewModel.activeModelPath.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()

    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val selectionCount = selected.size
    val inSelectionMode = selectionCount > 0
    val totalSelectedBytes = remember(selected, entries) {
        entries.filter { it.absolutePath in selected }.sumOf { it.sizeBytes }
    }
    val activeIsSelected = activeModelPath != null && activeModelPath in selected

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showConfirm = false },
            title = { Text(stringResource(R.string.models_delete_dialog_title, selectionCount)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.models_delete_dialog_message,
                            Formatter.formatFileSize(context, totalSelectedBytes)
                        )
                    )
                    if (activeIsSelected) {
                        Text(
                            text = stringResource(R.string.models_delete_dialog_active_warning),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isDeleting,
                    onClick = {
                        viewModel.deleteSelected { result ->
                            modelFileViewModel.loadCachedModels()
                            val msg = if (result.failures.isEmpty()) {
                                context.getString(
                                    R.string.models_deleted_toast,
                                    result.deletedCount,
                                    Formatter.formatFileSize(context, result.freedBytes)
                                )
                            } else {
                                context.getString(
                                    R.string.models_delete_partial_toast,
                                    result.deletedCount,
                                    result.failures.size
                                )
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            showConfirm = false
                        }
                    }
                ) { Text(stringResource(R.string.models_delete_action)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { showConfirm = false }
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (inSelectionMode)
                            stringResource(R.string.models_selected_count, selectionCount)
                        else
                            stringResource(R.string.models_title)
                    )
                },
                navigationIcon = {
                    if (inSelectionMode) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                Icons.Default.Close,
                                stringResource(R.string.models_clear_selection)
                            )
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.common_back)
                            )
                        }
                    }
                },
                actions = {
                    if (inSelectionMode) {
                        IconButton(onClick = { showConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                stringResource(R.string.models_delete_action)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.models_empty_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(entries, key = { it.absolutePath }) { entry ->
                        ModelRow(
                            entry = entry,
                            isSelected = entry.absolutePath in selected,
                            isActive = entry.absolutePath == activeModelPath,
                            onToggle = { viewModel.toggleSelection(entry.absolutePath) }
                        )
                    }
                }
            }
        }
    }
}

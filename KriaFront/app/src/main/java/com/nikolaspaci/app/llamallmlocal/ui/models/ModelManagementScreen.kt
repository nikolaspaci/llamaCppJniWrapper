package com.nikolaspaci.app.llamallmlocal.ui.models

import android.app.Activity
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.data.repository.CachedModelEntry
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    viewModel: ModelManagementViewModel,
    modelFileViewModel: ModelFileViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHuggingFace: () -> Unit
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val activeModelPath by viewModel.activeModelPath.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()

    var showConfirm by remember { mutableStateOf(false) }
    var showMmprojPicker by remember { mutableStateOf(false) }
    var mmprojTargetPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ggufLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                modelFileViewModel.cacheModel(uri) { newPath ->
                    if (newPath != null) viewModel.refresh()
                }
            }
        }
    }

    val mmprojLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val target = mmprojTargetPath
        if (target != null && result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                modelFileViewModel.cacheVisionAdapter(uri, target) { ok ->
                    if (ok) viewModel.refresh()
                }
            }
        }
        mmprojTargetPath = null
    }

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

    if (showMmprojPicker) {
        MmprojTargetDialog(
            baseModels = entries.filter { !it.isMmproj },
            onDismiss = { showMmprojPicker = false },
            onConfirm = { chosen ->
                showMmprojPicker = false
                mmprojTargetPath = chosen.absolutePath
                mmprojLauncher.launch(safIntent())
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    SectionHeader(stringResource(R.string.models_section_add))
                }
                item {
                    AddActionRow(
                        icon = Icons.Rounded.FolderOpen,
                        label = stringResource(R.string.models_action_browse_local),
                        onClick = { ggufLauncher.launch(safIntent()) }
                    )
                }
                item {
                    AddActionRow(
                        icon = Icons.Rounded.Image,
                        label = stringResource(R.string.models_action_import_mmproj),
                        onClick = { showMmprojPicker = true }
                    )
                }
                item {
                    AddActionRow(
                        icon = Icons.Rounded.CloudDownload,
                        label = stringResource(R.string.models_action_download_hf),
                        onClick = onNavigateToHuggingFace
                    )
                }
                item { HorizontalDivider() }
                item {
                    SectionHeader(stringResource(R.string.models_section_cached))
                }
                if (entries.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.models_empty_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
                        )
                    }
                } else {
                    items(entries, key = { it.absolutePath }) { entry ->
                        ModelRow(
                            entry = entry,
                            isSelected = entry.absolutePath in selected,
                            isActive = entry.absolutePath == activeModelPath,
                            inSelectionMode = inSelectionMode,
                            onToggle = { viewModel.toggleSelection(entry.absolutePath) },
                            onLongPress = { viewModel.toggleSelection(entry.absolutePath) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun AddActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun MmprojTargetDialog(
    baseModels: List<CachedModelEntry>,
    onDismiss: () -> Unit,
    onConfirm: (CachedModelEntry) -> Unit
) {
    var chosenPath by remember { mutableStateOf(baseModels.firstOrNull()?.absolutePath) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.models_mmproj_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.models_mmproj_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (baseModels.isEmpty()) {
                    Text(
                        text = stringResource(R.string.models_mmproj_dialog_empty),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        baseModels.forEach { entry ->
                            val isSelected = entry.absolutePath == chosenPath
                            ListItem(
                                leadingContent = {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { chosenPath = entry.absolutePath }
                                    )
                                },
                                headlineContent = { Text(entry.file.name) },
                                supportingContent = { Text(entry.parentDirName) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = isSelected,
                                        onClick = { chosenPath = entry.absolutePath }
                                    )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = chosenPath != null,
                onClick = {
                    val target = baseModels.firstOrNull { it.absolutePath == chosenPath } ?: return@Button
                    onConfirm(target)
                }
            ) { Text(stringResource(R.string.models_mmproj_dialog_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

private fun safIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = "*/*"
}

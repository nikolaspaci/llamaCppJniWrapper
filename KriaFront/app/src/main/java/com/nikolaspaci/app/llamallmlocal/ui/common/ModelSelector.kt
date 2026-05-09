package com.nikolaspaci.app.llamallmlocal.ui.common

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModel
import java.io.File

@Composable
fun ModelSelector(
    modelFileViewModel: ModelFileViewModel,
    selectedModelPath: String,
    onModelSelected: (String) -> Unit,
    onDownloadFromHuggingFace: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val cachedModels by modelFileViewModel.cachedModels.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var visionAdapterImported by remember { mutableStateOf(modelFileViewModel.hasVisionAdapter(selectedModelPath)) }
    val isModelMissing = remember(selectedModelPath) {
        selectedModelPath.isNotEmpty() && !File(selectedModelPath).exists()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                isLoading = true
                modelFileViewModel.cacheModel(uri) { newPath ->
                    isLoading = false
                    if (newPath != null) {
                        onModelSelected(newPath)
                    }
                }
            }
        }
    }

    val visionPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                isLoading = true
                modelFileViewModel.cacheVisionAdapter(uri, selectedModelPath) { success ->
                    isLoading = false
                    visionAdapterImported = success
                }
            }
        }
    }

    Column(modifier = modifier) {
        Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
            Button(onClick = { expanded = true }, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    val buttonText = if (selectedModelPath.isNotEmpty()) {
                        File(selectedModelPath).name
                    } else {
                        stringResource(R.string.model_selector_select)
                    }
                    Text(
                        text = buttonText,
                        color = if (isModelMissing) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                        } else {
                            Color.Unspecified
                        }
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
            cachedModels.forEach { file ->
                DropdownMenuItem(
                    text = { Text(file.name) },
                    onClick = {
                        onModelSelected(file.absolutePath)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.model_selector_browse_new)) },
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    filePickerLauncher.launch(intent)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.model_selector_import_vision)) },
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    visionPickerLauncher.launch(intent)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.model_selector_download_hf)) },
                onClick = {
                    onDownloadFromHuggingFace()
                    expanded = false
                }
            )
            }
        }
        if (isModelMissing) {
            Text(
                text = stringResource(R.string.model_selector_missing),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

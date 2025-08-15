package com.nikolaspaci.app.llamallmlocal.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModel
import java.io.File

@Composable
fun ModelSelectionDialog(
    viewModel: ModelFileViewModel,
    onDismissRequest: () -> Unit,
    onModelSelected: (String) -> Unit
) {
    val cachedModels by viewModel.cachedModels.collectAsState()
    var isLoading by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                isLoading = true
                // The ViewModel will handle caching and then the callback will select the model
                viewModel.cacheModel(uri) { newPath ->
                    isLoading = false
                    if (newPath != null) {
                        onModelSelected(newPath)
                    }
                    // Handle error case if needed
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select a Model") },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column {
                    Text("Choose a previously used model or browse for a new one.")
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(cachedModels) { file ->
                            ModelListItem(file = file) {
                                onModelSelected(file.absolutePath)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*" // GGUF files don't have a standard MIME type
                    }
                    filePickerLauncher.launch(intent)
                },
                enabled = !isLoading
            ) {
                Text("Browse New")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ModelListItem(file: File, onClick: () -> Unit) {
    Text(
        text = file.name,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    )
}

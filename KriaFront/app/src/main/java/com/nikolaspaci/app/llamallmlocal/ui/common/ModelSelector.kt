package com.nikolaspaci.app.llamallmlocal.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
    onNavigateToManageModels: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val cachedModels by modelFileViewModel.cachedModels.collectAsState()
    val isModelMissing = remember(selectedModelPath) {
        selectedModelPath.isNotEmpty() && !File(selectedModelPath).exists()
    }

    Column(modifier = modifier) {
        Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
            Button(onClick = { expanded = true }) {
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
                if (cachedModels.isNotEmpty()) {
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.model_selector_add_model)) },
                    onClick = {
                        expanded = false
                        onNavigateToManageModels()
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

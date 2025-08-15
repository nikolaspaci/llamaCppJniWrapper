package com.nikolaspaci.app.llamallmlocal.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nikolaspaci.app.llamallmlocal.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel
) {
    val modelParameter by viewModel.modelParameter.collectAsState()

    var temperature by remember { mutableStateOf("") }
    var topK by remember { mutableStateOf("") }
    var topP by remember { mutableStateOf("") }
    var minP by remember { mutableStateOf("") }

    modelParameter?.let {
        temperature = it.temperature.toString()
        topK = it.topK.toString()
        topP = it.topP.toString()
        minP = it.minP.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Model Settings") })
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            TextField(
                value = temperature,
                onValueChange = { temperature = it },
                label = { Text("Temperature") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = topK,
                onValueChange = { topK = it },
                label = { Text("Top K") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = topP,
                onValueChange = { topP = it },
                label = { Text("Top P") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = minP,
                onValueChange = { minP = it },
                label = { Text("Min P") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                viewModel.updateAndSave(
                    temperature = temperature.toFloatOrNull() ?: 0.8f,
                    topK = topK.toIntOrNull() ?: 40,
                    topP = topP.toFloatOrNull() ?: 0.95f,
                    minP = minP.toFloatOrNull() ?: 0.05f
                )
                navController.popBackStack()
            }) {
                Text("Save")
            }
        }
    }
}

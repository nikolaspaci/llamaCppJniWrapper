package com.nikolaspaci.app.llamallmlocal.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.data.database.SystemPromptPreset
import com.nikolaspaci.app.llamallmlocal.ui.common.ModelSelector
import com.nikolaspaci.app.llamallmlocal.util.HardwareCapabilities
import com.nikolaspaci.app.llamallmlocal.util.OptimalConfigurationService
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.SettingsUiState
import com.nikolaspaci.app.llamallmlocal.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modelId: String,
    conversationId: Long? = null,
    modelFileViewModel: ModelFileViewModel,
    onModelChanged: (String) -> Unit,
    onNavigateToManageModels: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentModelId by remember { mutableStateOf(modelId) }

    LaunchedEffect(modelId) {
        if (conversationId != null) {
            viewModel.loadParametersForConversation(conversationId, modelId)
        } else {
            viewModel.loadParameters(modelId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetToDefaults() }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.settings_reset))
                    }
                    val canSave = (uiState as? SettingsUiState.Ready)?.validationErrors?.isEmpty() == true
                    IconButton(
                        onClick = { viewModel.saveParameters() },
                        enabled = canSave
                    ) {
                        Icon(Icons.Default.Save, stringResource(R.string.common_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Model Selection section
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SettingsSection(title = stringResource(R.string.settings_section_model)) {
                ModelSelector(
                    modelFileViewModel = modelFileViewModel,
                    selectedModelPath = currentModelId,
                    onModelSelected = { newPath ->
                        currentModelId = newPath
                        onModelChanged(newPath)
                        if (conversationId != null) {
                            viewModel.loadParametersForConversation(conversationId, newPath)
                        } else {
                            viewModel.loadParameters(newPath)
                        }
                    },
                    onNavigateToManageModels = onNavigateToManageModels,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState) {
                is SettingsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is SettingsUiState.Ready -> {
                    val presets by viewModel.systemPromptPresets.collectAsState()
                    SettingsContent(
                        parameters = state.parameters,
                        errors = state.validationErrors,
                        presets = presets,
                        onParameterChange = { viewModel.updateParameter(it) },
                        onLoadPreset = { viewModel.loadSystemPromptPreset(it) },
                        onSaveAsPreset = { viewModel.saveCurrentAsPreset(it) },
                        onDeletePreset = { viewModel.deletePreset(it) },
                        modifier = Modifier
                    )
                }

                is SettingsUiState.Saved -> {
                    LaunchedEffect(Unit) {
                        onNavigateBack()
                    }
                }

                is SettingsUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    parameters: ModelParameter,
    errors: Map<String, String>,
    presets: List<SystemPromptPreset>,
    onParameterChange: (ModelParameter) -> Unit,
    onLoadPreset: (SystemPromptPreset) -> Unit,
    onSaveAsPreset: (String) -> Unit,
    onDeletePreset: (SystemPromptPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hardwareCapabilities = remember { HardwareCapabilities() }
    val capabilities = remember { hardwareCapabilities.detect(context) }
    val configService = remember { OptimalConfigurationService(context, hardwareCapabilities) }
    val suggestions = remember(parameters) { configService.getSuggestions(parameters) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Hardware Info
        HardwareInfoCard(capabilities = capabilities)

        // Optimization Suggestions
        OptimizationSuggestions(
            suggestions = suggestions,
            onApplySuggestion = { suggestion ->
                onParameterChange(suggestion.parameterChange(parameters))
            }
        )

        // System Prompt section
        SystemPromptSection(
            systemPrompt = parameters.systemPrompt,
            presets = presets,
            error = errors["systemPrompt"],
            onSystemPromptChange = { onParameterChange(parameters.copy(systemPrompt = it)) },
            onLoadPreset = onLoadPreset,
            onSaveAsPreset = onSaveAsPreset,
            onDeletePreset = onDeletePreset
        )

        // Section Sampling
        SettingsSection(title = stringResource(R.string.settings_section_sampling)) {
            ParameterSlider(
                label = stringResource(R.string.settings_temperature),
                value = parameters.temperature,
                onValueChange = { onParameterChange(parameters.copy(temperature = it)) },
                valueRange = ModelParameter.TEMPERATURE_RANGE,
                description = stringResource(R.string.settings_temperature_desc),
                error = errors["temperature"]
            )

            ParameterIntSlider(
                label = stringResource(R.string.settings_topk),
                value = parameters.topK,
                onValueChange = { onParameterChange(parameters.copy(topK = it)) },
                valueRange = ModelParameter.TOP_K_RANGE,
                description = stringResource(R.string.settings_topk_desc),
                error = errors["topK"]
            )

            ParameterSlider(
                label = stringResource(R.string.settings_topp),
                value = parameters.topP,
                onValueChange = { onParameterChange(parameters.copy(topP = it)) },
                valueRange = ModelParameter.TOP_P_RANGE,
                description = stringResource(R.string.settings_topp_desc),
                error = errors["topP"]
            )

            ParameterSlider(
                label = stringResource(R.string.settings_minp),
                value = parameters.minP,
                onValueChange = { onParameterChange(parameters.copy(minP = it)) },
                valueRange = ModelParameter.MIN_P_RANGE,
                description = stringResource(R.string.settings_minp_desc),
                error = errors["minP"]
            )

            ParameterSlider(
                label = stringResource(R.string.settings_repeat_penalty),
                value = parameters.repeatPenalty,
                onValueChange = { onParameterChange(parameters.copy(repeatPenalty = it)) },
                valueRange = ModelParameter.REPEAT_PENALTY_RANGE,
                description = stringResource(R.string.settings_repeat_penalty_desc),
                error = errors["repeatPenalty"]
            )
        }

        // Generation section
        SettingsSection(title = stringResource(R.string.settings_section_generation)) {
            val contextUnitLabel = stringResource(R.string.settings_context_size_unit, 0).replace("0", "%d")
            ParameterDropdown(
                label = stringResource(R.string.settings_context_size),
                value = parameters.contextSize,
                options = ModelParameter.CONTEXT_SIZE_VALUES,
                onValueChange = { onParameterChange(parameters.copy(contextSize = it)) },
                formatOption = { contextUnitLabel.format(it) },
                description = stringResource(R.string.settings_context_size_desc)
            )

            ParameterIntSlider(
                label = stringResource(R.string.settings_max_tokens),
                value = parameters.maxTokens,
                onValueChange = { onParameterChange(parameters.copy(maxTokens = it)) },
                valueRange = ModelParameter.MAX_TOKENS_RANGE,
                description = stringResource(R.string.settings_max_tokens_desc),
                error = errors["maxTokens"]
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.settings_enable_thinking), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.settings_enable_thinking_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = parameters.enableThinking,
                    onCheckedChange = { onParameterChange(parameters.copy(enableThinking = it)) }
                )
            }
        }

        // Section Performance
        SettingsSection(title = stringResource(R.string.settings_section_performance)) {
            ParameterIntSlider(
                label = stringResource(R.string.settings_threads_cpu),
                value = parameters.threadCount,
                onValueChange = { onParameterChange(parameters.copy(threadCount = it)) },
                valueRange = 1..ModelParameter.getMaxThreads(),
                description = stringResource(R.string.settings_threads_cpu_desc, ModelParameter.getMaxThreads()),
                error = errors["threadCount"]
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.settings_gpu_acceleration), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.settings_gpu_acceleration_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = parameters.useGpu,
                    onCheckedChange = { onParameterChange(parameters.copy(useGpu = it)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemPromptSection(
    systemPrompt: String,
    presets: List<SystemPromptPreset>,
    error: String?,
    onSystemPromptChange: (String) -> Unit,
    onLoadPreset: (SystemPromptPreset) -> Unit,
    onSaveAsPreset: (String) -> Unit,
    onDeletePreset: (SystemPromptPreset) -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetDropdownExpanded by remember { mutableStateOf(false) }
    var selectedPresetName by remember { mutableStateOf<String?>(null) }

    // Clear selection if the user edits the prompt manually (no longer matches a preset)
    val matchingPreset = presets.find { it.prompt == systemPrompt }
    val displayedName = matchingPreset?.name ?: selectedPresetName?.let { name ->
        // Reset if the prompt was changed after selecting
        if (presets.none { it.name == name && it.prompt == systemPrompt }) null else name
    }

    SettingsSection(title = stringResource(R.string.settings_section_system_prompt)) {
        // Preset dropdown
        if (presets.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = presetDropdownExpanded,
                onExpandedChange = { presetDropdownExpanded = it }
            ) {
                val loadPresetLabel = stringResource(R.string.settings_load_preset)
                OutlinedTextField(
                    value = displayedName ?: loadPresetLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = presetDropdownExpanded,
                    onDismissRequest = { presetDropdownExpanded = false }
                ) {
                    presets.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(preset.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        preset.prompt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            onClick = {
                                selectedPresetName = preset.name
                                onLoadPreset(preset)
                                presetDropdownExpanded = false
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onDeletePreset(preset) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.settings_delete_preset),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // System prompt text field
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = onSystemPromptChange,
            label = { Text(stringResource(R.string.settings_system_prompt_label)) },
            placeholder = { Text(stringResource(R.string.settings_system_prompt_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            minLines = 3,
            maxLines = 8,
            isError = error != null,
            supportingText = {
                val descLabel = stringResource(R.string.settings_system_prompt_desc)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = error ?: descLabel,
                        color = if (error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${systemPrompt.length} / ${ModelParameter.SYSTEM_PROMPT_MAX_LENGTH}",
                        color = if (error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        // Save as preset button
        if (systemPrompt.isNotBlank()) {
            OutlinedButton(
                onClick = { showSaveDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_save_as_preset))
            }
        }
    }

    // Save preset dialog
    if (showSaveDialog) {
        var presetName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.settings_save_preset_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text(stringResource(R.string.settings_preset_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (presetName.isNotBlank()) {
                            onSaveAsPreset(presetName.trim())
                            showSaveDialog = false
                        }
                    },
                    enabled = presetName.isNotBlank()
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider()
        content()
    }
}

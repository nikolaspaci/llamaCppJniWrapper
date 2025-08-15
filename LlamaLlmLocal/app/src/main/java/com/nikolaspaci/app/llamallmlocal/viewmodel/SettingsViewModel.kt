package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.data.repository.ModelParameterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: ModelParameterRepository,
    private val modelId: String
) : ViewModel() {

    private val _modelParameter = MutableStateFlow<ModelParameter?>(null)
    val modelParameter = _modelParameter.asStateFlow()

    init {
        viewModelScope.launch {
            _modelParameter.value = repository.getModelParameter(modelId) ?: ModelParameter(modelId)
        }
    }

    fun updateAndSave(temperature: Float, topK: Int, topP: Float, minP: Float) {
        val newParams = ModelParameter(
            modelId = modelId,
            temperature = temperature,
            topK = topK,
            topP = topP,
            minP = minP
        )
        _modelParameter.value = newParams
        viewModelScope.launch {
            repository.insert(newParams)
        }
    }
}

class SettingsViewModelFactory(
    private val repository: ModelParameterRepository,
    private val modelId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository, modelId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

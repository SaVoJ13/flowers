package com.flowers.app.ui.diagnosis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.model.DiagnosisResult
import com.flowers.app.data.model.SymptomCategory
import com.flowers.app.data.model.SymptomNode
import com.flowers.app.data.repository.FlowersRepository
import com.flowers.app.domain.SymptomCorrelationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiagnosisUiState(
    val plant: PlantEntity? = null,
    val categories: List<SymptomCategory> = emptyList(),
    val disclaimer: String = "",
    val selectedCategory: SymptomCategory? = null,
    val selectedSymptom: SymptomNode? = null,
    val result: DiagnosisResult? = null,
    val isLoading: Boolean = true
)

class DiagnosisViewModel(
    private val plantId: Long,
    private val repository: FlowersRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosisUiState())
    val uiState: StateFlow<DiagnosisUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val plant = repository.getPlantById(plantId)
            val tree = repository.getSymptomTree()
            _uiState.value = DiagnosisUiState(
                plant = plant,
                categories = tree.categories,
                disclaimer = tree.disclaimer_uk,
                isLoading = false
            )
        }
    }

    fun onCategorySelected(category: SymptomCategory) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            selectedSymptom = null,
            result = null
        )
    }

    fun onSymptomSelected(symptom: SymptomNode) {
        viewModelScope.launch {
            val lastWatering = repository.getLastWateringEvent(plantId)
            val result = SymptomCorrelationEngine.evaluate(
                symptom = symptom,
                lastWateredTimestamp = lastWatering?.timestamp
            )
            _uiState.value = _uiState.value.copy(
                selectedSymptom = symptom,
                result = result
            )
        }
    }

    fun resetSelection() {
        _uiState.value = _uiState.value.copy(
            selectedCategory = null,
            selectedSymptom = null,
            result = null
        )
    }

    fun saveDiagnosisToJournal(onSaved: () -> Unit) {
        val current = _uiState.value
        val result = current.result ?: return
        val symptom = current.selectedSymptom ?: return

        viewModelScope.launch {
            val noteText = buildString {
                append("Симптом: ${symptom.name_uk}\n")
                append("Ймовірна причина: ${result.probableCause}\n")
                if (result.contextNote != null) {
                    append("${result.contextNote}\n")
                }
                append("План дій: ${result.actionPlan.joinToString("; ")}")
            }
            repository.logCareEvent(
                CareEventEntity(
                    plantId = plantId,
                    eventType = "CHECK_STATE",
                    notes = noteText
                )
            )
            onSaved()
        }
    }

    class Factory(
        private val plantId: Long,
        private val repository: FlowersRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DiagnosisViewModel(plantId, repository) as T
        }
    }
}

package com.flowers.app.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.remote.GeneratePlantResult
import com.flowers.app.data.repository.FlowersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogUiState(
    val plants: List<CatalogPlantEntity> = emptyList(),
    val generalPods: List<CarePodEntity> = emptyList(),
    val searchQuery: String = "",
    val isGenerating: Boolean = false,
    val generationError: String? = null,
    val isPremium: Boolean = true
)

class CatalogViewModel(
    private val repository: FlowersRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isGenerating = MutableStateFlow(false)
    private val _generationError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CatalogUiState> = combine(
        repository.getAllCatalogPlants(),
        repository.getGeneralPods(),
        _searchQuery,
        _isGenerating,
        _generationError
    ) { allPlants, pods, query, generating, error ->
        val trimmed = query.trim()
        val filteredPlants = if (trimmed.isBlank()) {
            allPlants
        } else {
            allPlants.filter { plant ->
                plant.nameUk.contains(trimmed, ignoreCase = true) ||
                plant.nameLatin.contains(trimmed, ignoreCase = true) ||
                plant.popularName.contains(trimmed, ignoreCase = true)
            }
        }
        CatalogUiState(
            plants = filteredPlants,
            generalPods = pods,
            searchQuery = query,
            isGenerating = generating,
            generationError = error,
            isPremium = repository.userPreferences.isPremium
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CatalogUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearSearchQuery() {
        _searchQuery.value = ""
    }

    fun generatePlantWithAi(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            _isGenerating.value = true
            _generationError.value = null
            when (val result = repository.generatePlantWithAi(trimmed)) {
                is GeneratePlantResult.Success -> {
                    _isGenerating.value = false
                    _searchQuery.value = result.plant.nameUk
                }
                is GeneratePlantResult.Error -> {
                    _isGenerating.value = false
                    _generationError.value = result.message
                }
            }
        }
    }

    fun clearGenerationError() {
        _generationError.value = null
    }

    class Factory(private val repository: FlowersRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CatalogViewModel(repository) as T
        }
    }
}

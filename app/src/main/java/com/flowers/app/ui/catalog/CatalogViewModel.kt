package com.flowers.app.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.repository.FlowersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CatalogUiState(
    val plants: List<CatalogPlantEntity> = emptyList(),
    val generalPods: List<CarePodEntity> = emptyList(),
    val searchQuery: String = ""
)

class CatalogViewModel(
    private val repository: FlowersRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<CatalogUiState> = combine(
        repository.getAllCatalogPlants(),
        repository.getGeneralPods(),
        _searchQuery
    ) { allPlants, pods, query ->
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
            searchQuery = query
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

    class Factory(private val repository: FlowersRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CatalogViewModel(repository) as T
        }
    }
}

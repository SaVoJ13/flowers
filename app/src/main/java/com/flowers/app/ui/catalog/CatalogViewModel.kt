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
import kotlinx.coroutines.flow.flatMapLatest
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

    val uiState: StateFlow<CatalogUiState> = _searchQuery.flatMapLatest { query ->
        val plantsFlow = if (query.isBlank()) {
            repository.getAllCatalogPlants()
        } else {
            repository.searchCatalog(query)
        }
        val podsFlow = repository.getGeneralPods()

        combine(plantsFlow, podsFlow) { plants, pods ->
            CatalogUiState(
                plants = plants,
                generalPods = pods,
                searchQuery = query
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CatalogUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    class Factory(private val repository: FlowersRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CatalogViewModel(repository) as T
        }
    }
}

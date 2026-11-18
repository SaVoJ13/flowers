package com.flowers.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.repository.FlowersRepository
import com.flowers.app.domain.ReminderCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayUiState(
    val duePlants: List<PlantEntity> = emptyList(),
    val upcomingPlants: List<PlantEntity> = emptyList(),
    val generalPods: List<CarePodEntity> = emptyList(),
    val isLoading: Boolean = false
)

class TodayViewModel(
    private val repository: FlowersRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)

    val uiState: StateFlow<TodayUiState> = combine(
        repository.getAllActivePlants(),
        repository.getGeneralPods(),
        _isLoading
    ) { allPlants, generalPods, isLoading ->
        val now = System.currentTimeMillis()
        val due = allPlants.filter { ReminderCalculator.isDue(it.nextCheckDate, now) }
        val upcoming = allPlants.filter { !ReminderCalculator.isDue(it.nextCheckDate, now) }
        TodayUiState(
            duePlants = due,
            upcomingPlants = upcoming,
            generalPods = generalPods,
            isLoading = isLoading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayUiState(isLoading = true)
    )

    fun onWaterClicked(plant: PlantEntity) {
        viewModelScope.launch {
            repository.performWatering(plant)
        }
    }

    fun onSnoozeClicked(plant: PlantEntity, days: Int) {
        viewModelScope.launch {
            repository.snoozePlant(plant, days)
        }
    }

    fun onSkipClicked(plant: PlantEntity) {
        viewModelScope.launch {
            repository.skipPlant(plant)
        }
    }

    class Factory(private val repository: FlowersRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TodayViewModel(repository) as T
        }
    }
}

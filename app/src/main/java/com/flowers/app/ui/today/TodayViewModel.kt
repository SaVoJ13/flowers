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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayUiState(
    val duePlants: List<PlantEntity> = emptyList(),
    val upcomingPlants: List<PlantEntity> = emptyList(),
    val generalPods: List<CarePodEntity> = emptyList(),
    val isLoading: Boolean = false,
    val processingPlantIds: Set<Long> = emptySet(),
    val plantToConfirmWatering: PlantEntity? = null
)

class TodayViewModel(
    private val repository: FlowersRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _processingPlantIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _plantToConfirmWatering = MutableStateFlow<PlantEntity?>(null)

    val uiState: StateFlow<TodayUiState> = combine(
        repository.getAllActivePlants(),
        repository.getGeneralPods(),
        _isLoading,
        _processingPlantIds,
        _plantToConfirmWatering
    ) { allPlants, generalPods, isLoading, processingIds, plantToConfirm ->
        val now = System.currentTimeMillis()
        val due = allPlants.filter { ReminderCalculator.isDue(it.nextCheckDate, now) }
        val upcoming = allPlants.filter { !ReminderCalculator.isDue(it.nextCheckDate, now) }
        TodayUiState(
            duePlants = due,
            upcomingPlants = upcoming,
            generalPods = generalPods,
            isLoading = isLoading,
            processingPlantIds = processingIds,
            plantToConfirmWatering = plantToConfirm
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayUiState(isLoading = true)
    )

    /**
     * Request watering: opens explicit confirmation dialog to prevent accidental clicks.
     */
    fun onWaterClicked(plant: PlantEntity) {
        if (_processingPlantIds.value.contains(plant.id)) return
        _plantToConfirmWatering.value = plant
    }

    /**
     * User confirms actual watering occurred.
     */
    fun onWaterConfirmed() {
        val plant = _plantToConfirmWatering.value ?: return
        _plantToConfirmWatering.value = null
        if (_processingPlantIds.value.contains(plant.id)) return
        _processingPlantIds.update { it + plant.id }

        viewModelScope.launch {
            try {
                repository.performWatering(plant)
            } finally {
                _processingPlantIds.update { it - plant.id }
            }
        }
    }

    /**
     * Dismiss watering confirmation dialog without saving.
     */
    fun onWaterConfirmationDismissed() {
        _plantToConfirmWatering.value = null
    }

    /**
     * Postpone soil check by 2 calendar days because soil is still moist.
     * Does NOT update lastWateredDate.
     */
    fun onSnoozeTwoDaysClicked(plant: PlantEntity) {
        if (_processingPlantIds.value.contains(plant.id)) return
        _processingPlantIds.update { it + plant.id }

        viewModelScope.launch {
            try {
                repository.snoozePlant(plant, days = 2)
            } finally {
                _processingPlantIds.update { it - plant.id }
            }
        }
    }

    /**
     * Custom snooze if triggered via menu.
     */
    fun onSnoozeClicked(plant: PlantEntity, days: Int) {
        if (_processingPlantIds.value.contains(plant.id)) return
        _processingPlantIds.update { it + plant.id }

        viewModelScope.launch {
            try {
                repository.snoozePlant(plant, days)
            } finally {
                _processingPlantIds.update { it - plant.id }
            }
        }
    }

    fun onSkipClicked(plant: PlantEntity) {
        if (_processingPlantIds.value.contains(plant.id)) return
        _processingPlantIds.update { it + plant.id }

        viewModelScope.launch {
            try {
                repository.skipPlant(plant)
            } finally {
                _processingPlantIds.update { it - plant.id }
            }
        }
    }

    class Factory(private val repository: FlowersRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TodayViewModel(repository) as T
        }
    }
}

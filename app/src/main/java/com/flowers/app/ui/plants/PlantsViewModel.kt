package com.flowers.app.ui.plants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.repository.FlowersRepository
import com.flowers.app.domain.ReminderCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlantsListUiState(
    val plants: List<PlantEntity> = emptyList(),
    val rooms: List<String> = listOf("Всі"),
    val selectedRoom: String = "Всі"
)

data class PlantDetailUiState(
    val plant: PlantEntity? = null,
    val species: CatalogPlantEntity? = null,
    val pods: List<CarePodEntity> = emptyList(),
    val events: List<CareEventEntity> = emptyList(),
    val isLoading: Boolean = true
)

class PlantsViewModel(
    private val repository: FlowersRepository
) : ViewModel() {

    private val _selectedRoom = MutableStateFlow("Всі")

    val listUiState: StateFlow<PlantsListUiState> = combine(
        repository.getAllActivePlants(),
        _selectedRoom
    ) { allPlants, selectedRoom ->
        val rooms = listOf("Всі") + allPlants.map { it.roomLocation }.distinct().sorted()
        val filtered = if (selectedRoom == "Всі") allPlants else allPlants.filter { it.roomLocation == selectedRoom }
        PlantsListUiState(
            plants = filtered,
            rooms = rooms,
            selectedRoom = selectedRoom
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlantsListUiState()
    )

    fun onRoomSelected(room: String) {
        _selectedRoom.value = room
    }

    fun getPlantDetail(plantId: Long): StateFlow<PlantDetailUiState> {
        return repository.getPlantFlowById(plantId).flatMapLatest { plant ->
            if (plant == null) {
                flowOf(PlantDetailUiState(isLoading = false))
            } else {
                val speciesFlow = if (plant.speciesId != null) {
                    flowOf(repository.getCatalogPlantById(plant.speciesId))
                } else {
                    flowOf(null)
                }
                val podsFlow = if (plant.speciesId != null) {
                    repository.getPodsForSpecies(plant.speciesId)
                } else {
                    repository.getGeneralPods()
                }
                val eventsFlow = repository.getEventsForPlant(plantId)

                combine(speciesFlow, podsFlow, eventsFlow) { species, pods, events ->
                    PlantDetailUiState(
                        plant = plant,
                        species = species,
                        pods = pods,
                        events = events,
                        isLoading = false
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlantDetailUiState(isLoading = true)
        )
    }

    fun savePlant(
        plantId: Long?,
        customName: String,
        speciesId: String?,
        roomLocation: String,
        lightLevel: String,
        checkIntervalDays: Int,
        photoUri: String?,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (plantId == null || plantId == 0L) {
                val nextCheck = ReminderCalculator.calculateNextCheckAfterWatering(checkIntervalDays, now)
                val newPlant = PlantEntity(
                    customName = customName,
                    speciesId = speciesId,
                    roomLocation = roomLocation.ifBlank { "Вітальня" },
                    lightLevel = lightLevel,
                    checkIntervalDays = checkIntervalDays,
                    lastWateredDate = now,
                    nextCheckDate = nextCheck,
                    photoUri = photoUri
                )
                val insertedId = repository.insertPlant(newPlant)
                repository.logCareEvent(
                    CareEventEntity(
                        plantId = insertedId,
                        eventType = "WATER",
                        notes = "Рослину додано до колекції"
                    )
                )
            } else {
                val existing = repository.getPlantById(plantId)
                if (existing != null) {
                    val updated = existing.copy(
                        customName = customName,
                        speciesId = speciesId,
                        roomLocation = roomLocation.ifBlank { "Вітальня" },
                        lightLevel = lightLevel,
                        checkIntervalDays = checkIntervalDays,
                        photoUri = photoUri ?: existing.photoUri
                    )
                    repository.updatePlant(updated)
                }
            }
            onSaved()
        }
    }

    fun deletePlant(plant: PlantEntity, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deletePlant(plant)
            onDeleted()
        }
    }

    fun performWatering(plant: PlantEntity) {
        viewModelScope.launch {
            repository.performWatering(plant)
        }
    }

    class Factory(private val repository: FlowersRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlantsViewModel(repository) as T
        }
    }
}

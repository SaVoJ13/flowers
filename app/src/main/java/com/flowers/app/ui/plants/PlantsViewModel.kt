package com.flowers.app.ui.plants

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flowers.app.data.image.ImageProfile
import com.flowers.app.data.image.ImageProcessor
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.remote.IdentifyPlantResult
import com.flowers.app.data.remote.PlantIdentificationResponse
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
import java.io.File

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

/**
 * Стан майстра додавання рослини Flowers 2.0 (TASK-053).
 */
sealed class AddPlantUiState {
    object Idle : AddPlantUiState()
    object ProcessingPhoto : AddPlantUiState()
    object AnalyzingWithAi : AddPlantUiState()
    data class Confirmation(
        val photoFile: File,
        val identification: PlantIdentificationResponse,
        val isOfflineFallback: Boolean = false
    ) : AddPlantUiState()
    data class NotAPlantError(
        val photoFile: File,
        val message: String
    ) : AddPlantUiState()
    data class ErrorFallback(
        val photoFile: File?,
        val message: String,
        val isOffline: Boolean
    ) : AddPlantUiState()
}

class PlantsViewModel(
    private val repository: FlowersRepository,
    private val imageProcessorProvider: ((Context) -> ImageProcessor)? = null
) : ViewModel() {

    private val _selectedRoom = MutableStateFlow("Всі")
    private val _addPlantUiState = MutableStateFlow<AddPlantUiState>(AddPlantUiState.Idle)
    val addPlantUiState: StateFlow<AddPlantUiState> = _addPlantUiState.asStateFlow()

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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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

    fun onPhotoSelected(uri: Uri, context: Context) {
        viewModelScope.launch {
            _addPlantUiState.value = AddPlantUiState.ProcessingPhoto
            val processor = imageProcessorProvider?.invoke(context) ?: ImageProcessor(context)
            val processResult = processor.processImage(uri, ImageProfile.SpeciesIdentification)
            val imageResult = processResult.getOrNull()
            if (imageResult == null) {
                val error = processResult.exceptionOrNull()
                _addPlantUiState.value = AddPlantUiState.ErrorFallback(
                    photoFile = null,
                    message = error?.message ?: "Не вдалося обробити фотографію.",
                    isOffline = false
                )
                return@launch
            }

            val photoFile = imageResult.file
            _addPlantUiState.value = AddPlantUiState.AnalyzingWithAi
            val identifyResult = repository.identifyPlantFromPhoto(photoFile)
            when (identifyResult) {
                is IdentifyPlantResult.Success -> {
                    _addPlantUiState.value = AddPlantUiState.Confirmation(
                        photoFile = photoFile,
                        identification = identifyResult.response
                    )
                }
                is IdentifyPlantResult.NotAPlant -> {
                    _addPlantUiState.value = AddPlantUiState.NotAPlantError(
                        photoFile = photoFile,
                        message = identifyResult.message
                    )
                }
                is IdentifyPlantResult.Error -> {
                    _addPlantUiState.value = AddPlantUiState.ErrorFallback(
                        photoFile = photoFile,
                        message = identifyResult.message,
                        isOffline = identifyResult.isOffline
                    )
                }
            }
        }
    }

    fun onManualEntryRequested(photoFile: File? = null) {
        _addPlantUiState.value = AddPlantUiState.Confirmation(
            photoFile = photoFile ?: File(""),
            identification = PlantIdentificationResponse(
                isPlant = true,
                nameUk = "",
                nameLatin = "",
                confidence = "LOW",
                description = null,
                lightLevel = "INDIRECT",
                defaultCheckIntervalDays = 7,
                wateringAdvice = null,
                soilType = null,
                toxicityInfo = "UNKNOWN",
                alternativeSpecies = emptyList()
            ),
            isOfflineFallback = true
        )
    }

    fun resetAddPlantFlow(discardPhoto: Boolean = true) {
        if (discardPhoto) {
            val currentState = _addPlantUiState.value
            val fileToDelete = when (currentState) {
                is AddPlantUiState.Confirmation -> currentState.photoFile
                is AddPlantUiState.NotAPlantError -> currentState.photoFile
                is AddPlantUiState.ErrorFallback -> currentState.photoFile
                else -> null
            }
            fileToDelete?.let {
                if (it.exists() && it.isFile) {
                    it.delete()
                }
            }
        }
        _addPlantUiState.value = AddPlantUiState.Idle
    }

    fun savePlant(
        plantId: Long?,
        customName: String,
        speciesId: String? = null,
        roomLocation: String = "Вітальня",
        lightLevel: String = "INDIRECT",
        checkIntervalDays: Int = 7,
        photoUri: String? = null,
        speciesName: String? = null,
        botanicalName: String? = null,
        description: String? = null,
        wateringAdvice: String? = null,
        soilType: String? = null,
        toxicityInfo: String? = null,
        aiConfidence: String? = null,
        isAiGenerated: Boolean = false,
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
                    photoUri = photoUri,
                    speciesName = speciesName,
                    botanicalName = botanicalName,
                    description = description,
                    wateringAdvice = wateringAdvice,
                    soilType = soilType,
                    toxicityInfo = toxicityInfo,
                    aiConfidence = aiConfidence,
                    isAiGenerated = isAiGenerated
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
                        speciesId = speciesId ?: existing.speciesId,
                        roomLocation = roomLocation.ifBlank { "Вітальня" },
                        lightLevel = lightLevel,
                        checkIntervalDays = checkIntervalDays,
                        photoUri = photoUri ?: existing.photoUri,
                        speciesName = speciesName ?: existing.speciesName,
                        botanicalName = botanicalName ?: existing.botanicalName,
                        description = description ?: existing.description,
                        wateringAdvice = wateringAdvice ?: existing.wateringAdvice,
                        soilType = soilType ?: existing.soilType,
                        toxicityInfo = toxicityInfo ?: existing.toxicityInfo,
                        aiConfidence = aiConfidence ?: existing.aiConfidence,
                        isAiGenerated = if (isAiGenerated) true else existing.isAiGenerated
                    )
                    repository.updatePlant(updated)
                }
            }
            _addPlantUiState.value = AddPlantUiState.Idle
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

    class Factory(
        private val repository: FlowersRepository,
        private val imageProcessorProvider: ((Context) -> ImageProcessor)? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlantsViewModel(repository, imageProcessorProvider) as T
        }
    }
}

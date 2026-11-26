package com.flowers.app.ui.diagnosis

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flowers.app.data.image.ImageProcessor
import com.flowers.app.data.image.ImageProfile
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.model.DiagnosisResult
import com.flowers.app.data.model.SymptomCategory
import com.flowers.app.data.model.SymptomNode
import com.flowers.app.data.remote.DiagnosePlantHealthResult
import com.flowers.app.data.remote.PlantHealthDiagnosisResponse
import com.flowers.app.data.repository.FlowersRepository
import com.flowers.app.domain.SymptomCorrelationEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class DiagnosisMode {
    MODE_SELECTION,
    PHOTO_DIAGNOSIS,
    MANUAL_SYMPTOMS
}

sealed class PhotoDiagnosisStep {
    object PickPhoto : PhotoDiagnosisStep()
    data class ProcessingPhoto(val uri: Uri) : PhotoDiagnosisStep()
    data class PreviewAndConfirm(val photoFile: File) : PhotoDiagnosisStep()
    data class AnalyzingWithAi(val photoFile: File) : PhotoDiagnosisStep()
    data class Success(val photoFile: File, val diagnosis: PlantHealthDiagnosisResponse) : PhotoDiagnosisStep()
    data class Error(val photoFile: File?, val message: String, val isOffline: Boolean = false) : PhotoDiagnosisStep()
}

data class DiagnosisUiState(
    val plant: PlantEntity? = null,
    val mode: DiagnosisMode = DiagnosisMode.MODE_SELECTION,
    val photoStep: PhotoDiagnosisStep = PhotoDiagnosisStep.PickPhoto,
    // Поля існуючої ручної діагностики (збережено без змін)
    val categories: List<SymptomCategory> = emptyList(),
    val disclaimer: String = "",
    val selectedCategory: SymptomCategory? = null,
    val selectedSymptom: SymptomNode? = null,
    val result: DiagnosisResult? = null,
    val isLoading: Boolean = true,
    // Захист від повторних натискань (дебаунс)
    val isSaving: Boolean = false
)

class DiagnosisViewModel(
    private val plantId: Long,
    private val repository: FlowersRepository,
    private val imageProcessorProvider: ((Context) -> ImageProcessor)? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosisUiState())
    val uiState: StateFlow<DiagnosisUiState> = _uiState.asStateFlow()

    private var pendingPhotoFile: File? = null
    private var activeAnalysisJob: Job? = null

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val plant = repository.getPlantById(plantId)
            val tree = repository.getSymptomTree()
            _uiState.update {
                it.copy(
                    plant = plant,
                    categories = tree.categories,
                    disclaimer = tree.disclaimer_uk,
                    isLoading = false
                )
            }
        }
    }

    // ==========================================
    // Режими діагностики
    // ==========================================

    fun selectMode(mode: DiagnosisMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun resetToModeSelection() {
        cancelActivePhotoOperation()
        _uiState.update {
            it.copy(
                mode = DiagnosisMode.MODE_SELECTION,
                photoStep = PhotoDiagnosisStep.PickPhoto,
                selectedCategory = null,
                selectedSymptom = null,
                result = null
            )
        }
    }

    // ==========================================
    // Фото-діагностика ШІ (TASK-055)
    // ==========================================

    /**
     * Пряме призначення підготовленого файлу фотографії (наприклад, у тестах).
     */
    fun onPhotoSelected(file: File) {
        cancelActivePhotoOperation()
        pendingPhotoFile = file
        _uiState.update { it.copy(photoStep = PhotoDiagnosisStep.PreviewAndConfirm(file)) }
    }

    fun onPhotoSelected(uri: Uri, context: Context) {
        cancelActivePhotoOperation()

        activeAnalysisJob = viewModelScope.launch {
            _uiState.update { it.copy(photoStep = PhotoDiagnosisStep.ProcessingPhoto(uri)) }
            val processor = imageProcessorProvider?.invoke(context) ?: ImageProcessor(context)
            val processResult = processor.processImage(uri, ImageProfile.DiseaseDiagnosis)
            val imageResult = processResult.getOrNull()

            if (imageResult == null) {
                val error = processResult.exceptionOrNull()
                if (!isActive) return@launch
                _uiState.update {
                    it.copy(
                        photoStep = PhotoDiagnosisStep.Error(
                            photoFile = null,
                            message = error?.message ?: "Не вдалося обробити фотографію.",
                            isOffline = false
                        )
                    )
                }
                return@launch
            }

            val photoFile = imageResult.file
            pendingPhotoFile = photoFile

            if (!isActive) {
                cleanUpPendingPhoto()
                return@launch
            }

            // Перехід до кроку попереднього перегляду та підтвердження аналізу
            _uiState.update { it.copy(photoStep = PhotoDiagnosisStep.PreviewAndConfirm(photoFile)) }
        }
    }

    /**
     * Користувач підтверджує перегляд фотографії та запускає аналіз ШІ.
     */
    fun onConfirmAnalysis() {
        val photoFile = pendingPhotoFile ?: return
        val plant = _uiState.value.plant ?: return

        activeAnalysisJob?.cancel()
        activeAnalysisJob = viewModelScope.launch {
            _uiState.update { it.copy(photoStep = PhotoDiagnosisStep.AnalyzingWithAi(photoFile)) }
            val diagnosisResult = repository.diagnosePlantHealth(photoFile, plant)

            if (!isActive) {
                cleanUpPendingPhoto()
                return@launch
            }

            when (diagnosisResult) {
                is DiagnosePlantHealthResult.Success -> {
                    _uiState.update {
                        it.copy(photoStep = PhotoDiagnosisStep.Success(photoFile, diagnosisResult.response))
                    }
                }
                is DiagnosePlantHealthResult.NotAPlant -> {
                    _uiState.update {
                        it.copy(
                            photoStep = PhotoDiagnosisStep.Error(
                                photoFile = photoFile,
                                message = diagnosisResult.message,
                                isOffline = false
                            )
                        )
                    }
                }
                is DiagnosePlantHealthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            photoStep = PhotoDiagnosisStep.Error(
                                photoFile = photoFile,
                                message = diagnosisResult.message,
                                isOffline = diagnosisResult.isOffline
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Скасування активного запиту або вибір іншої фотографії.
     */
    fun cancelActivePhotoOperation() {
        activeAnalysisJob?.cancel()
        activeAnalysisJob = null
        cleanUpPendingPhoto()
        _uiState.update { it.copy(photoStep = PhotoDiagnosisStep.PickPhoto) }
    }

    /**
     * Збереження результату фото-діагностики ШІ в історію рослини (HEALTH_CHECK).
     */
    fun savePhotoDiagnosisToHistory(onSaved: () -> Unit) {
        if (_uiState.value.isSaving) return
        val currentStep = _uiState.value.photoStep
        if (currentStep !is PhotoDiagnosisStep.Success) return

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val diagnosis = currentStep.diagnosis
                val photoPath = currentStep.photoFile.absolutePath

                val noteText = buildString {
                    append("Ймовірна причина: ${diagnosis.probableCause}\n")
                    append("Оцінка впевненості: ${diagnosis.confidence} (орієнтовно)\n")
                    if (diagnosis.visibleSymptoms.isNotEmpty()) {
                        append("Видимі симптоми: ${diagnosis.visibleSymptoms.joinToString(", ")}\n")
                    }
                    if (diagnosis.alternativeCauses.isNotEmpty()) {
                        append("Альтернативні пояснення: ${diagnosis.alternativeCauses.joinToString(", ")}\n")
                    }
                    if (diagnosis.firstAidSteps.isNotEmpty()) {
                        append("План дій:\n")
                        diagnosis.firstAidSteps.forEachIndexed { index, step ->
                            append("${index + 1}. $step\n")
                        }
                    }
                    if (diagnosis.followUpAdvice.isNotBlank()) {
                        append("Повторний огляд: ${diagnosis.followUpAdvice}\n")
                    }
                    append("⚠️ ${diagnosis.preliminaryNotice}")
                }.trim()

                repository.logCareEvent(
                    CareEventEntity(
                        plantId = plantId,
                        eventType = "HEALTH_CHECK",
                        timestamp = System.currentTimeMillis(),
                        notes = noteText,
                        photoUri = photoPath
                    )
                )

                // Фото успішно закріплено за історією — очищати при виході не потрібно
                pendingPhotoFile = null
                onSaved()
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // ==========================================
    // Ручна діагностика симптомів (дерево рішень)
    // ==========================================

    fun onCategorySelected(category: SymptomCategory) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                selectedSymptom = null,
                result = null
            )
        }
    }

    fun onSymptomSelected(symptom: SymptomNode) {
        viewModelScope.launch {
            val lastWatering = repository.getLastWateringEvent(plantId)
            val result = SymptomCorrelationEngine.evaluate(
                symptom = symptom,
                lastWateredTimestamp = lastWatering?.timestamp
            )
            _uiState.update {
                it.copy(
                    selectedSymptom = symptom,
                    result = result
                )
            }
        }
    }

    fun resetSelection() {
        _uiState.update {
            it.copy(
                selectedCategory = null,
                selectedSymptom = null,
                result = null
            )
        }
    }

    fun saveDiagnosisToJournal(onSaved: () -> Unit) {
        if (_uiState.value.isSaving) return
        val current = _uiState.value
        val result = current.result ?: return
        val symptom = current.selectedSymptom ?: return

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
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
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun cleanUpPendingPhoto() {
        pendingPhotoFile?.let { file ->
            try {
                if (file.exists() && file.isFile) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
        pendingPhotoFile = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelActivePhotoOperation()
    }

    class Factory(
        private val plantId: Long,
        private val repository: FlowersRepository,
        private val imageProcessorProvider: ((Context) -> ImageProcessor)? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DiagnosisViewModel(plantId, repository, imageProcessorProvider) as T
        }
    }
}

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
    val isSaving: Boolean = false,
    val saveErrorMessage: String? = null
)

/**
 * Модель володіння файлом фотографії у поточному сеансі діагностики.
 * Лише файли з [isOwnedForDeletion] = true (створені ImageProcessor у межах поточної операції)
 * дозволено видаляти при скасуванні.
 * [operationToken] гарантує, що старий запит не може видалити файл нової операції.
 */
data class OwnedPhoto(
    val file: File,
    val isOwnedForDeletion: Boolean,
    val operationToken: Long
)

class DiagnosisViewModel(
    private val plantId: Long,
    private val repository: FlowersRepository,
    private val imageProcessorProvider: ((Context) -> ImageProcessor)? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosisUiState())
    val uiState: StateFlow<DiagnosisUiState> = _uiState.asStateFlow()

    private var currentOwnedPhoto: OwnedPhoto? = null
    private var operationTokenCounter: Long = 0L
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
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(mode = mode, saveErrorMessage = null) }
    }

    fun resetToModeSelection() {
        if (_uiState.value.isSaving) return
        cancelActivePhotoOperation()
        _uiState.update {
            it.copy(
                mode = DiagnosisMode.MODE_SELECTION,
                photoStep = PhotoDiagnosisStep.PickPhoto,
                selectedCategory = null,
                selectedSymptom = null,
                result = null,
                saveErrorMessage = null
            )
        }
    }

    // ==========================================
    // Фото-діагностика ШІ (TASK-055)
    // ==========================================

    /**
     * Пряме призначення підготовленого файлу фотографії (наприклад, у зовнішніх інтерфейсах).
     * Зовнішній файл [file] ніколи не вважається тимчасовим файлом для видалення (isOwnedForDeletion = false).
     */
    fun onPhotoSelected(file: File) {
        if (_uiState.value.isSaving) return
        onPhotoSelectedInternal(file, isOwnedForDeletion = false)
    }

    /**
     * Спеціальний метод для тестів, що дозволяє явно перевірити логіку життєвого циклу володіння файлом.
     */
    @androidx.annotation.VisibleForTesting
    fun onPhotoSelectedForTesting(file: File, isOwnedForDeletion: Boolean) {
        if (_uiState.value.isSaving) return
        onPhotoSelectedInternal(file, isOwnedForDeletion = isOwnedForDeletion)
    }

    private fun onPhotoSelectedInternal(file: File, isOwnedForDeletion: Boolean) {
        cancelActivePhotoOperation()
        val token = ++operationTokenCounter
        currentOwnedPhoto = OwnedPhoto(
            file = file,
            isOwnedForDeletion = isOwnedForDeletion,
            operationToken = token
        )
        _uiState.update { it.copy(photoStep = PhotoDiagnosisStep.PreviewAndConfirm(file), saveErrorMessage = null) }
    }

    fun onPhotoSelected(uri: Uri, context: Context, onProcessed: (() -> Unit)? = null) {
        if (_uiState.value.isSaving) return
        cancelActivePhotoOperation()
        val token = ++operationTokenCounter

        activeAnalysisJob = viewModelScope.launch {
            _uiState.update { it.copy(photoStep = PhotoDiagnosisStep.ProcessingPhoto(uri), saveErrorMessage = null) }
            val processor = imageProcessorProvider?.invoke(context) ?: ImageProcessor(context)
            val processResult = processor.processImage(uri, ImageProfile.DiseaseDiagnosis)
            val imageResult = processResult.getOrNull()

            try {
                onProcessed?.invoke()
            } catch (_: Throwable) {}

            // Перевіряємо актуальність операції
            if (!isActive || token != operationTokenCounter) {
                // Якщо операція застаріла або скасована, видаляємо тільки щойно створений тимчасовий файл
                imageResult?.file?.let { staleFile ->
                    try { if (staleFile.exists() && staleFile.isFile) staleFile.delete() } catch (_: Throwable) {}
                }
                return@launch
            }

            if (imageResult == null) {
                val error = processResult.exceptionOrNull()
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
            // Тільки файл, успішно створений ImageProcessor для поточної активної операції, має право на видалення
            currentOwnedPhoto = OwnedPhoto(
                file = photoFile,
                isOwnedForDeletion = true,
                operationToken = token
            )

            // Перехід до кроку попереднього перегляду та підтвердження аналізу
            _uiState.update { it.copy(photoStep = PhotoDiagnosisStep.PreviewAndConfirm(photoFile)) }
        }
    }

    /**
     * Користувач підтверджує перегляд фотографії та запускає аналіз ШІ.
     */
    fun onConfirmAnalysis() {
        if (_uiState.value.isSaving) return
        val owned = currentOwnedPhoto ?: return
        val photoFile = owned.file
        val plant = _uiState.value.plant ?: return
        val token = owned.operationToken

        activeAnalysisJob?.cancel()
        activeAnalysisJob = viewModelScope.launch {
            _uiState.update { it.copy(photoStep = PhotoDiagnosisStep.AnalyzingWithAi(photoFile), saveErrorMessage = null) }
            val diagnosisResult = repository.diagnosePlantHealth(photoFile, plant)

            // Перевіряємо актуальність поточної операції (захист від затриманих відповідей старих запитів)
            if (!isActive || token != operationTokenCounter) {
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
        if (_uiState.value.isSaving) return
        activeAnalysisJob?.cancel()
        activeAnalysisJob = null
        cleanUpOwnedPhoto()
        _uiState.update { it.copy(photoStep = PhotoDiagnosisStep.PickPhoto, saveErrorMessage = null) }
    }

    /**
     * Збереження результату фото-діагностики ШІ в історію рослини (HEALTH_CHECK).
     */
    fun savePhotoDiagnosisToHistory(onSaved: () -> Unit) {
        if (_uiState.value.isSaving) return
        val currentStep = _uiState.value.photoStep
        if (currentStep !is PhotoDiagnosisStep.Success) return

        _uiState.update { it.copy(isSaving = true, saveErrorMessage = null) }

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
                    } else if (!diagnosis.confidence.equals("HIGH", ignoreCase = true)) {
                        append("Альтернативні пояснення: надійні альтернативи не визначені ШІ. Рекомендовано перевірку за ручним аналізом симптомів.\n")
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

                // 1. Спочатку виконуємо запис у базу даних Room.
                // Право власності (isOwnedForDeletion = true) зберігається, допоки запис не підтверджено успіхом.
                repository.logCareEvent(
                    CareEventEntity(
                        plantId = plantId,
                        eventType = "HEALTH_CHECK",
                        timestamp = System.currentTimeMillis(),
                        notes = noteText,
                        photoUri = photoPath
                    )
                )

                // 2. Лише після підтвердженого успішного запису знімаємо право на видалення фото
                currentOwnedPhoto = currentOwnedPhoto?.copy(isOwnedForDeletion = false)
                currentOwnedPhoto = null

                onSaved()
            } catch (e: Exception) {
                // При помилці бази даних фото залишається під контролем (isOwnedForDeletion = true),
                // файл не видаляється, а користувач бачить повідомлення про помилку та можливість повторити збереження.
                _uiState.update {
                    it.copy(
                        saveErrorMessage = "Не вдалося зберегти запис у базі даних: ${e.message ?: "помилка збереження"}. Спробуйте ще раз."
                    )
                }
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

    private fun cleanUpOwnedPhoto() {
        val owned = currentOwnedPhoto ?: return
        if (owned.isOwnedForDeletion) {
            try {
                if (owned.file.exists() && owned.file.isFile) {
                    owned.file.delete()
                }
            } catch (_: Exception) {}
        }
        currentOwnedPhoto = null
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

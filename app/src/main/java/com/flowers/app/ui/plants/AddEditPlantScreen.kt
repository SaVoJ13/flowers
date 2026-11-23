package com.flowers.app.ui.plants

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.repository.FlowersRepository
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPlantScreen(
    plantId: Long?,
    viewModel: PlantsViewModel,
    repository: FlowersRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isEditMode = plantId != null && plantId != 0L

    if (isEditMode) {
        EditPlantContent(
            plantId = plantId!!,
            viewModel = viewModel,
            repository = repository,
            onNavigateBack = onNavigateBack
        )
    } else {
        AddPlantPhotoFlowContent(
            viewModel = viewModel,
            onNavigateBack = onNavigateBack
        )
    }
}

/**
 * Flowers 2.0: Додавання рослини за фото з ШІ-розпізнаванням та ручним підтвердженням (TASK-053).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlantPhotoFlowContent(
    viewModel: PlantsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val addPlantState by viewModel.addPlantUiState.collectAsState()

    // Налаштування тимчасового файлу для зйомки камерою
    val cameraTempFile = remember {
        File(context.cacheDir, "camera/camera_temp_${System.currentTimeMillis()}.jpg").apply {
            parentFile?.mkdirs()
        }
    }
    val cameraUri = remember(cameraTempFile) {
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraTempFile)
        } catch (_: Exception) {
            null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            viewModel.onPhotoSelected(cameraUri, context)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onPhotoSelected(uri, context)
        }
    }

    // Системна кнопка «Назад»
    BackHandler {
        if (addPlantState !is AddPlantUiState.Idle) {
            viewModel.resetAddPlantFlow(discardPhoto = true)
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (addPlantState) {
                            is AddPlantUiState.Confirmation -> "Підтвердження рослини"
                            is AddPlantUiState.AnalyzingWithAi -> "Розпізнавання..."
                            is AddPlantUiState.ProcessingPhoto -> "Обробка фото..."
                            else -> "Нова рослина"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (addPlantState !is AddPlantUiState.Idle) {
                                viewModel.resetAddPlantFlow(discardPhoto = true)
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = addPlantState) {
                is AddPlantUiState.Idle -> {
                    AddPlantSourceSelection(
                        onCameraClick = {
                            cameraUri?.let { cameraLauncher.launch(it) }
                        },
                        onGalleryClick = {
                            galleryLauncher.launch("image/*")
                        },
                        onManualClick = {
                            viewModel.onManualEntryRequested()
                        }
                    )
                }

                is AddPlantUiState.ProcessingPhoto -> {
                    ProgressStateView(
                        title = "Оптимізація фотографії...",
                        subtitle = "Масштабуємо зображення та видаляємо метадані для безпеки",
                        onCancel = { viewModel.resetAddPlantFlow(discardPhoto = true) }
                    )
                }

                is AddPlantUiState.AnalyzingWithAi -> {
                    ProgressStateView(
                        title = "ШІ розпізнає вид рослини...",
                        subtitle = "Аналізуємо габітус, визначаємо вимоги до світла та ґрунту",
                        onCancel = { viewModel.resetAddPlantFlow(discardPhoto = true) }
                    )
                }

                is AddPlantUiState.NotAPlantError -> {
                    ErrorStateView(
                        icon = Icons.Default.Warning,
                        title = "Рослину не виявлено",
                        message = state.message,
                        primaryButtonText = "Спробувати інше фото",
                        onPrimaryClick = { viewModel.resetAddPlantFlow(discardPhoto = true) },
                        secondaryButtonText = "Заповнити дані вручну",
                        onSecondaryClick = { viewModel.onManualEntryRequested(state.photoFile) }
                    )
                }

                is AddPlantUiState.ErrorFallback -> {
                    ErrorStateView(
                        icon = if (state.isOffline) Icons.Default.Info else Icons.Default.Warning,
                        title = if (state.isOffline) "Немає зв'язку з інтернетом" else "Помилка розпізнавання ШІ",
                        message = state.message,
                        primaryButtonText = "Заповнити дані вручну",
                        onPrimaryClick = { viewModel.onManualEntryRequested(state.photoFile) },
                        secondaryButtonText = "Спробувати інше фото",
                        onSecondaryClick = { viewModel.resetAddPlantFlow(discardPhoto = true) }
                    )
                }

                is AddPlantUiState.Confirmation -> {
                    PlantConfirmationForm(
                        state = state,
                        viewModel = viewModel,
                        onSave = { name, species, botanical, room, light, interval, advice, soil, toxicity, confidence, isAi ->
                            viewModel.savePlant(
                                plantId = null,
                                customName = name,
                                speciesId = null,
                                roomLocation = room,
                                lightLevel = light,
                                checkIntervalDays = interval,
                                photoUri = state.photoFile.takeIf { it.exists() && it.length() > 0 }?.absolutePath,
                                speciesName = species,
                                botanicalName = botanical,
                                description = state.identification.description,
                                wateringAdvice = advice,
                                soilType = soil,
                                toxicityInfo = toxicity,
                                aiConfidence = confidence,
                                isAiGenerated = isAi,
                                onSaved = onNavigateBack
                            )
                        },
                        onCancel = {
                            viewModel.resetAddPlantFlow(discardPhoto = true)
                            onNavigateBack()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Екран вибору джерела фото або ручного введення.
 */
@Composable
private fun AddPlantSourceSelection(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onManualClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Додавання рослини за фото",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Сфотографуйте вашу кімнатну рослину або оберіть фото з галереї. ШІ автоматично розпізнає вид, визначить правильне освітлення та базовий інтервал огляду ґрунту.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                onClick = onCameraClick,
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Камера",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Камера",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Card(
                onClick = onGalleryClick,
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Галерея",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Галерея",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onManualClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Ввести дані вручну без фото",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Індикатор стану обробки/розпізнавання.
 */
@Composable
private fun ProgressStateView(
    title: String,
    subtitle: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(onClick = onCancel) {
            Text("Скасувати")
        }
    }
}

/**
 * Відображення помилки або відсутності мережі з можливістю продовжити вручну.
 */
@Composable
private fun ErrorStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String,
    onSecondaryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onPrimaryClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(primaryButtonText)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onSecondaryClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(secondaryButtonText)
        }
    }
}

/**
 * Екран обов'язкового підтвердження картки рослини Flowers 2.0 (TASK-053).
 */
@Composable
private fun PlantConfirmationForm(
    state: AddPlantUiState.Confirmation,
    viewModel: PlantsViewModel,
    onSave: (
        customName: String,
        speciesName: String,
        botanicalName: String,
        roomLocation: String,
        lightLevel: String,
        checkIntervalDays: Int,
        wateringAdvice: String,
        soilType: String,
        toxicityInfo: String,
        aiConfidence: String,
        isAiGenerated: Boolean
    ) -> Unit,
    onCancel: () -> Unit
) {
    val isSaving by viewModel.isSaving.collectAsState()
    var hasSwitchedToAlternative by remember { mutableStateOf(false) }

    var customName by remember {
        mutableStateOf(
            state.identification.nameUk.ifBlank { "Мій вазон" }
        )
    }
    var speciesName by remember { mutableStateOf(state.identification.nameUk) }
    var botanicalName by remember { mutableStateOf(state.identification.nameLatin) }
    var roomLocation by remember { mutableStateOf("Вітальня") }
    var lightLevel by remember { mutableStateOf(state.identification.lightLevel) }
    var checkIntervalDays by remember {
        mutableFloatStateOf(state.identification.defaultCheckIntervalDays.toFloat())
    }
    var wateringAdvice by remember { mutableStateOf(state.identification.wateringAdvice.orEmpty()) }
    var soilType by remember { mutableStateOf(state.identification.soilType.orEmpty()) }
    var toxicityInfo by remember { mutableStateOf(state.identification.toxicityInfo ?: "UNKNOWN") }

    val commonRooms = listOf("Вітальня", "Спальня", "Кухня", "Балкон", "Кабінет")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // 1. Попередній перегляд фото
        if (state.photoFile.exists() && state.photoFile.length() > 0) {
            AsyncImage(
                model = state.photoFile,
                contentDescription = "Фото рослини",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        }

        // 2. Бейдж впевненості ШІ
        ConfidenceBadge(
            confidence = if (hasSwitchedToAlternative) "LOW" else state.identification.confidence,
            isOffline = state.isOfflineFallback,
            isAlternative = hasSwitchedToAlternative
        )

        // 3. Альтернативні варіанти виду (якщо є)
        if (state.identification.alternativeSpecies.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Схожі варіанти виду (натисніть для вибору):",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.identification.alternativeSpecies.forEach { alt ->
                        FilterChip(
                            selected = speciesName == alt,
                            onClick = {
                                if (speciesName != alt) {
                                    speciesName = alt
                                    if (customName == state.identification.nameUk || customName == "Мій вазон") {
                                        customName = alt
                                    }
                                    // Безпека: при виборі іншого виду скидаємо залежні рекомендації попереднього виду
                                    botanicalName = ""
                                    wateringAdvice = ""
                                    soilType = ""
                                    toxicityInfo = "UNKNOWN"
                                    hasSwitchedToAlternative = true
                                }
                            },
                            label = { Text(alt) }
                        )
                    }
                }

                if (hasSwitchedToAlternative) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Обрано альтернативний вид: рекомендації попереднього виду скинуто для безпеки. Будь ласка, вкажіть або перевірте умови догляду.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        // 4. Назва вазона
        OutlinedTextField(
            value = customName,
            onValueChange = { customName = it },
            label = { Text("Власна назва вазона *") },
            placeholder = { Text("Напр. Монстера на підвіконні") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // 5. Вид та ботанічна назва
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = speciesName,
                onValueChange = { speciesName = it },
                label = { Text("Вид (укр.)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = botanicalName,
                onValueChange = { botanicalName = it },
                label = { Text("Латиною") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        // 6. Розташування (кімната)
        Text(
            text = "Розташування (кімната):",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            commonRooms.take(3).forEach { room ->
                FilterChip(
                    selected = roomLocation == room,
                    onClick = { roomLocation = room },
                    label = { Text(room) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            commonRooms.drop(3).forEach { room ->
                FilterChip(
                    selected = roomLocation == room,
                    onClick = { roomLocation = room },
                    label = { Text(room) }
                )
            }
        }

        // 7. Освітлення
        Text(
            text = "Освітлення:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = lightLevel == "SHADE",
                onClick = { lightLevel = "SHADE" },
                label = { Text("Тінь") }
            )
            FilterChip(
                selected = lightLevel == "INDIRECT",
                onClick = { lightLevel = "INDIRECT" },
                label = { Text("Розсіяне світло") }
            )
            FilterChip(
                selected = lightLevel == "DIRECT",
                onClick = { lightLevel = "DIRECT" },
                label = { Text("Пряме сонце") }
            )
        }

        // 8. Інтервал перевірки ґрунту
        Text(
            text = "Інтервал перевірки ґрунту: ${checkIntervalDays.toInt()} дн.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = checkIntervalDays,
            onValueChange = { checkIntervalDays = it },
            valueRange = 3f..21f,
            steps = 17,
            modifier = Modifier.fillMaxWidth()
        )

        // 9. Рекомендації ШІ
        if (wateringAdvice.isNotBlank() || soilType.isNotBlank() || toxicityInfo.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Поради щодо догляду:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (wateringAdvice.isNotBlank()) {
                        Text(
                            text = "💧 Полив: $wateringAdvice",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (soilType.isNotBlank()) {
                        Text(
                            text = "🌱 Ґрунт: $soilType",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (toxicityInfo.isNotBlank() && toxicityInfo != "UNKNOWN") {
                        Text(
                            text = "🐾 Безпека: $toxicityInfo",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 10. Кнопки збереження та скасування (із захистом від повторних натискань)
        Button(
            onClick = {
                if (customName.isNotBlank() && !isSaving) {
                    onSave(
                        customName.trim(),
                        speciesName.trim(),
                        botanicalName.trim(),
                        roomLocation,
                        lightLevel,
                        checkIntervalDays.toInt(),
                        wateringAdvice.trim(),
                        soilType.trim(),
                        toxicityInfo.trim(),
                        if (hasSwitchedToAlternative) "LOW" else state.identification.confidence,
                        !state.isOfflineFallback && !hasSwitchedToAlternative
                    )
                }
            },
            enabled = customName.isNotBlank() && !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = if (isSaving) "Збереження..." else "Зберегти в мої рослини",
                style = MaterialTheme.typography.titleMedium
            )
        }

        OutlinedButton(
            onClick = onCancel,
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Скасувати")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ConfidenceBadge(
    confidence: String,
    isOffline: Boolean,
    isAlternative: Boolean = false
) {
    val (text, containerColor, contentColor, icon) = when {
        isAlternative -> Quadruple(
            "Ручний вибір (альтернативний вид)",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Default.Info
        )
        isOffline -> Quadruple(
            "Ручне введення (офлайн)",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Info
        )
        confidence.equals("HIGH", ignoreCase = true) -> Quadruple(
            "Висока впевненість ШІ",
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            Icons.Default.CheckCircle
        )
        confidence.equals("MEDIUM", ignoreCase = true) -> Quadruple(
            "Середня впевненість ШІ — перевірте назву",
            Color(0xFFFFF8E1),
            Color(0xFFF57F17),
            Icons.Default.Warning
        )
        else -> Quadruple(
            "Низька впевненість ШІ",
            Color(0xFFFFEBEE),
            Color(0xFFC62828),
            Icons.Default.Info
        )
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

/**
 * Редагування існуючої рослини (сумісність із Flowers 1.0).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPlantContent(
    plantId: Long,
    viewModel: PlantsViewModel,
    repository: FlowersRepository,
    onNavigateBack: () -> Unit
) {
    val catalogPlants by repository.getAllCatalogPlants().collectAsState(initial = emptyList())
    val isSaving by viewModel.isSaving.collectAsState()

    var customName by remember { mutableStateOf("") }
    var selectedSpecies by remember { mutableStateOf<CatalogPlantEntity?>(null) }
    var roomLocation by remember { mutableStateOf("Вітальня") }
    var lightLevel by remember { mutableStateOf("INDIRECT") }
    var checkIntervalDays by remember { mutableFloatStateOf(7f) }
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(plantId, catalogPlants) {
        val plant = repository.getPlantById(plantId)
        if (plant != null) {
            customName = plant.customName
            roomLocation = plant.roomLocation
            lightLevel = plant.lightLevel
            checkIntervalDays = plant.checkIntervalDays.toFloat()
            if (plant.speciesId != null) {
                selectedSpecies = catalogPlants.find { it.id == plant.speciesId }
                    ?: repository.getCatalogPlantById(plant.speciesId)
            }
        }
    }

    val commonRooms = listOf("Вітальня", "Спальня", "Кухня", "Балкон", "Кабінет")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редагувати рослину") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text("Власна назва вазона *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            ExposedDropdownMenuBox(
                expanded = isExpanded,
                onExpandedChange = { isExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedSpecies?.let { "${it.nameUk} (${it.nameLatin})" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Ботанічний вид із каталогу") },
                    placeholder = { Text("Оберіть вид із каталогу (необов'язково)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Немає в списку / Свій вид") },
                        onClick = {
                            selectedSpecies = null
                            isExpanded = false
                        }
                    )
                    catalogPlants.forEach { species ->
                        DropdownMenuItem(
                            text = { Text("${species.nameUk} (${species.nameLatin})") },
                            onClick = {
                                selectedSpecies = species
                                if (customName.isBlank()) {
                                    customName = species.nameUk
                                }
                                checkIntervalDays = species.defaultCheckIntervalDays.toFloat()
                                isExpanded = false
                            }
                        )
                    }
                }
            }

            Text(
                text = "Розташування (кімната):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                commonRooms.take(3).forEach { room ->
                    FilterChip(
                        selected = roomLocation == room,
                        onClick = { roomLocation = room },
                        label = { Text(room) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                commonRooms.drop(3).forEach { room ->
                    FilterChip(
                        selected = roomLocation == room,
                        onClick = { roomLocation = room },
                        label = { Text(room) }
                    )
                }
            }

            Text(
                text = "Освітлення:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = lightLevel == "SHADE",
                    onClick = { lightLevel = "SHADE" },
                    label = { Text("Тінь") }
                )
                FilterChip(
                    selected = lightLevel == "INDIRECT",
                    onClick = { lightLevel = "INDIRECT" },
                    label = { Text("Розсіяне світло") }
                )
                FilterChip(
                    selected = lightLevel == "DIRECT",
                    onClick = { lightLevel = "DIRECT" },
                    label = { Text("Пряме сонце") }
                )
            }

            Text(
                text = "Інтервал перевірки ґрунту: ${checkIntervalDays.toInt()} дн.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = checkIntervalDays,
                onValueChange = { checkIntervalDays = it },
                valueRange = 3f..21f,
                steps = 17,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (customName.isNotBlank() && !isSaving) {
                        viewModel.savePlant(
                            plantId = plantId,
                            customName = customName.trim(),
                            speciesId = selectedSpecies?.id,
                            roomLocation = roomLocation,
                            lightLevel = lightLevel,
                            checkIntervalDays = checkIntervalDays.toInt(),
                            photoUri = null,
                            onSaved = onNavigateBack
                        )
                    }
                },
                enabled = customName.isNotBlank() && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (isSaving) "Збереження..." else "Зберегти зміни",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

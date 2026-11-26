package com.flowers.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.domain.SoilCheckAdvisor
import com.flowers.app.ui.components.CarePodCard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onNavigateToAddPlant: () -> Unit,
    onNavigateToPlantDetail: (Long) -> Unit,
    onNavigateToDiagnosis: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.onScreenResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val todayDateFormatted = remember {
        SimpleDateFormat("EEEE, d MMMM", Locale("uk")).format(Date())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Сьогодні",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = todayDateFormatted.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Section 1: Due Today
            item {
                Text(
                    text = "Потребують перевірки ґрунту (${uiState.duePlants.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.duePlants.isEmpty()) {
                item {
                    EmptyTodayCard(
                        hasAnyPlants = uiState.upcomingPlants.isNotEmpty(),
                        onAddPlantClicked = onNavigateToAddPlant
                    )
                }
            } else {
                items(uiState.duePlants, key = { it.id }) { plant ->
                    val isProcessing = uiState.processingPlantIds.contains(plant.id)
                    DuePlantCard(
                        plant = plant,
                        isProcessing = isProcessing,
                        onWaterClicked = { viewModel.onWaterClicked(plant) },
                        onSnoozeTwoDaysClicked = { viewModel.onSnoozeTwoDaysClicked(plant) },
                        onSnoozeCustomClicked = { days -> viewModel.onSnoozeClicked(plant, days) },
                        onSkipClicked = { viewModel.onSkipClicked(plant) },
                        onDiagnosisClicked = { onNavigateToDiagnosis(plant.id) },
                        onCardClicked = { onNavigateToPlantDetail(plant.id) }
                    )
                }
            }

            // Section 2: General Beginner Care Pods
            if (uiState.generalPods.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "💡 Підказки для початківців",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.generalPods) { pod ->
                            CarePodCard(pod = pod)
                        }
                    }
                }
            }

            // Section 3: Upcoming plants
            if (uiState.upcomingPlants.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Найближчі перевірки (${uiState.upcomingPlants.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(uiState.upcomingPlants, key = { it.id }) { plant ->
                    UpcomingPlantCard(
                        plant = plant,
                        onCardClicked = { onNavigateToPlantDetail(plant.id) }
                    )
                }
            }
        }
    }

    // Explicit confirmation dialog for actual watering
    if (uiState.plantToConfirmWatering != null) {
        val plant = uiState.plantToConfirmWatering!!
        AlertDialog(
            onDismissRequest = { viewModel.onWaterConfirmationDismissed() },
            title = {
                Text(
                    text = "Підтвердження поливу",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Ґрунт сухий і рослину «${plant.customName}» дійсно щойно полито?\n\nФіксуйте подію лише після фактичного фізичного поливу."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onWaterConfirmed() },
                    modifier = Modifier.testTag("confirm_water_button")
                ) {
                    Text("Полито")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.onWaterConfirmationDismissed() },
                    modifier = Modifier.testTag("cancel_water_button")
                ) {
                    Text("Скасувати")
                }
            }
        )
    }
}

@Composable
fun DuePlantCard(
    plant: PlantEntity,
    isProcessing: Boolean,
    onWaterClicked: () -> Unit,
    onSnoozeTwoDaysClicked: () -> Unit,
    onSnoozeCustomClicked: (Int) -> Unit,
    onSkipClicked: () -> Unit,
    onDiagnosisClicked: () -> Unit,
    onCardClicked: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showSnoozeMenu by remember { mutableStateOf(false) }

    val adaptiveAdvice = remember(plant) {
        SoilCheckAdvisor.getSoilCheckAdvice(plant)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClicked),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Photo or Park Icon
                val photoFile = plant.photoUri?.let { File(it) }
                if (photoFile != null && photoFile.exists() && photoFile.isFile && photoFile.length() > 0) {
                    AsyncImage(
                        model = photoFile,
                        contentDescription = plant.customName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Park,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plant.customName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val speciesOrRoom = buildString {
                        append(plant.roomLocation)
                        val species = plant.speciesName ?: plant.botanicalName
                        if (!species.isNullOrBlank()) {
                            append(" • ")
                            append(species)
                        }
                    }
                    Text(
                        text = speciesOrRoom,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Опції")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Пропустити цей цикл") },
                                onClick = {
                                    showMenu = false
                                    onSkipClicked()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Відкласти на 1 день") },
                                onClick = {
                                    showMenu = false
                                    onSnoozeCustomClicked(1)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Відкласти на 3 дні") },
                                onClick = {
                                    showMenu = false
                                    onSnoozeCustomClicked(3)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Є проблема / Діагностика") },
                                onClick = {
                                    showMenu = false
                                    onDiagnosisClicked()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Adaptive Advice Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("advice_box_${plant.id}")
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "💡",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = adaptiveAdvice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("advice_text_${plant.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: WATER vs DELAY
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onWaterClicked,
                    enabled = !isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("water_button_${plant.id}")
                        .semantics { contentDescription = "Ґрунт перевірено — Полито" },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Полито",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onSnoozeTwoDaysClicked,
                    enabled = !isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("snooze_button_${plant.id}")
                        .semantics { contentDescription = "Ще вологий — Відкласти на 2 дні" },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ще вологий (на 2 дні)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun UpcomingPlantCard(
    plant: PlantEntity,
    onCardClicked: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("d MMMM", Locale("uk")) }
    val nextDate = remember(plant.nextCheckDate) { dateFormat.format(Date(plant.nextCheckDate)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClicked),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Park,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plant.customName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = plant.roomLocation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = nextDate,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun EmptyTodayCard(
    hasAnyPlants: Boolean,
    onAddPlantClicked: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Park,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (hasAnyPlants) "Усі вазони перевірені!" else "У вас ще немає рослин",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasAnyPlants)
                    "Чудова робота! Жодної простроченої перевірки. Наступні перевірки заплановані на найближчі дні."
                else
                    "Додайте першу рослину, щоб налаштувати перевірку ґрунту та захистити її від переливу.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (!hasAnyPlants) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAddPlantClicked,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Додати рослину")
                }
            }
        }
    }
}

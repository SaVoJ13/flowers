package com.flowers.app.ui.plants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.repository.FlowersRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPlantScreen(
    plantId: Long?,
    viewModel: PlantsViewModel,
    repository: FlowersRepository,
    onNavigateBack: () -> Unit
) {
    val catalogPlants by repository.getAllCatalogPlants().collectAsState(initial = emptyList())

    var customName by remember { mutableStateOf("") }
    var selectedSpecies by remember { mutableStateOf<CatalogPlantEntity?>(null) }
    var roomLocation by remember { mutableStateOf("Вітальня") }
    var lightLevel by remember { mutableStateOf("INDIRECT") }
    var checkIntervalDays by remember { mutableFloatStateOf(7f) }
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(plantId, catalogPlants) {
        if (plantId != null) {
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
    }

    val commonRooms = listOf("Вітальня", "Спальня", "Кухня", "Балкон", "Кабінет")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (plantId == null) "Нова рослина" else "Редагувати рослину") },
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
            // 1. Plant name
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text("Власна назва вазона *") },
                placeholder = { Text("Напр. Монстера на підвіконні") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 2. Select Species from Catalog
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

            // 3. Room Selection
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

            // 4. Light Level
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

            // 5. Check interval
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

            // Save Button
            Button(
                onClick = {
                    if (customName.isNotBlank()) {
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
                enabled = customName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Зберегти рослину", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

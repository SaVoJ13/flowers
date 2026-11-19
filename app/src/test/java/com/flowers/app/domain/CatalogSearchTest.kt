package com.flowers.app.domain

import com.flowers.app.data.local.entity.CatalogPlantEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSearchTest {

    private val sampleCatalog = listOf(
        CatalogPlantEntity(
            id = "monstera_deliciosa",
            nameUk = "Монстера деліціозна",
            nameLatin = "Monstera deliciosa",
            popularName = "Монстера",
            difficulty = "EASY",
            light = "INDIRECT",
            defaultCheckIntervalDays = 7,
            soilType = "Пухкий",
            toxicToPets = true
        ),
        CatalogPlantEntity(
            id = "ficus_benjamina",
            nameUk = "Фікус бенджаміна",
            nameLatin = "Ficus benjamina",
            popularName = "Плакучий інжир",
            difficulty = "MEDIUM",
            light = "BRIGHT",
            defaultCheckIntervalDays = 6,
            soilType = "Дренований",
            toxicToPets = true
        ),
        CatalogPlantEntity(
            id = "sansevieria_trifasciata",
            nameUk = "Сансевієрія трисмугова",
            nameLatin = "Sansevieria trifasciata",
            popularName = "Щучий хвіст",
            difficulty = "VERY_EASY",
            light = "LOW_TO_BRIGHT",
            defaultCheckIntervalDays = 14,
            soilType = "Для сукулентів",
            toxicToPets = true
        ),
        CatalogPlantEntity(
            id = "spathiphyllum_wallisii",
            nameUk = "Спатифілум Волліса",
            nameLatin = "Spathiphyllum wallisii",
            popularName = "Жіноче щастя",
            difficulty = "EASY",
            light = "MEDIUM",
            defaultCheckIntervalDays = 5,
            soilType = "Вологоємний",
            toxicToPets = true
        ),
        CatalogPlantEntity(
            id = "aloe_vera",
            nameUk = "Алое вера",
            nameLatin = "Aloe vera",
            popularName = "Столітник",
            difficulty = "VERY_EASY",
            light = "BRIGHT_DIRECT",
            defaultCheckIntervalDays = 12,
            soilType = "Піщаний",
            toxicToPets = true
        )
    )

    private fun filterPlants(plants: List<CatalogPlantEntity>, query: String): List<CatalogPlantEntity> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return plants
        return plants.filter { plant ->
            plant.nameUk.contains(trimmed, ignoreCase = true) ||
            plant.nameLatin.contains(trimmed, ignoreCase = true) ||
            plant.popularName.contains(trimmed, ignoreCase = true)
        }
    }

    @Test
    fun `search by lowercase Cyrillic matches titlecase nameUk`() {
        val results = filterPlants(sampleCatalog, "монстера")
        assertEquals(1, results.size)
        assertEquals("monstera_deliciosa", results[0].id)
    }

    @Test
    fun `search by uppercase Cyrillic matches mixedcase nameUk`() {
        val results = filterPlants(sampleCatalog, "ФІКУС")
        assertEquals(1, results.size)
        assertEquals("ficus_benjamina", results[0].id)
    }

    @Test
    fun `search with Ukrainian specific letter i matches correctly`() {
        val results = filterPlants(sampleCatalog, "спатифілум")
        assertEquals(1, results.size)
        assertEquals("spathiphyllum_wallisii", results[0].id)
    }

    @Test
    fun `search by popular folk name matches correctly`() {
        val results = filterPlants(sampleCatalog, "щучий хвіст")
        assertEquals(1, results.size)
        assertEquals("sansevieria_trifasciata", results[0].id)

        val results2 = filterPlants(sampleCatalog, "жіноче щастя")
        assertEquals(1, results2.size)
        assertEquals("spathiphyllum_wallisii", results2[0].id)
    }

    @Test
    fun `search by Latin botanical name matches ignoreCase`() {
        val results = filterPlants(sampleCatalog, "monstera")
        assertEquals(1, results.size)
        assertEquals("monstera_deliciosa", results[0].id)

        val results2 = filterPlants(sampleCatalog, "TRIFASCIATA")
        assertEquals(1, results2.size)
        assertEquals("sansevieria_trifasciata", results2[0].id)
    }

    @Test
    fun `search with leading and trailing whitespace is trimmed`() {
        val results = filterPlants(sampleCatalog, "   алое   ")
        assertEquals(1, results.size)
        assertEquals("aloe_vera", results[0].id)
    }

    @Test
    fun `search for non-existent plant returns empty list`() {
        val results = filterPlants(sampleCatalog, "баобаб")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `blank search returns all plants`() {
        val results = filterPlants(sampleCatalog, "   ")
        assertEquals(sampleCatalog.size, results.size)
    }
}

package com.flowers.app.data

import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogPrepopulationTest {

    private val gson = Gson()

    @Test
    fun `PLANT_CATALOG json deserializes all 20 species with non-null properties`() {
        val file = File("src/main/assets/content/PLANT_CATALOG.json")
        assertTrue("PLANT_CATALOG.json must exist in assets", file.exists())

        val json = file.readText()
        val type = object : TypeToken<List<CatalogPlantEntity>>() {}.type
        val plants: List<CatalogPlantEntity> = gson.fromJson(json, type)

        assertEquals("Catalog should contain exactly 20 species", 20, plants.size)

        plants.forEach { plant ->
            assertNotNull("id must not be null for ${plant.id}", plant.id)
            assertTrue("id must not be blank", plant.id.isNotBlank())

            assertNotNull("nameUk must not be null for ${plant.id}", plant.nameUk)
            assertTrue("nameUk must not be blank for ${plant.id}", plant.nameUk.isNotBlank())

            assertNotNull("nameLatin must not be null for ${plant.id}", plant.nameLatin)
            assertTrue("nameLatin must not be blank for ${plant.id}", plant.nameLatin.isNotBlank())

            assertNotNull("popularName must not be null for ${plant.id}", plant.popularName)
            assertTrue("popularName must not be blank for ${plant.id}", plant.popularName.isNotBlank())

            assertTrue("defaultCheckIntervalDays must be > 0 for ${plant.id}", plant.defaultCheckIntervalDays > 0)
            assertTrue("soilType must not be blank for ${plant.id}", plant.soilType.isNotBlank())
            assertTrue("difficulty must not be blank for ${plant.id}", plant.difficulty.isNotBlank())
            assertTrue("light must not be blank for ${plant.id}", plant.light.isNotBlank())
        }

        // Spot-check specific plants
        val zamio = plants.firstOrNull { it.id == "zamioculcas" }
        assertNotNull(zamio)
        assertEquals("Заміокулькас", zamio!!.nameUk)
        assertEquals("Zamioculcas zamiifolia", zamio.nameLatin)
        assertEquals("Доларове дерево", zamio.popularName)
        assertEquals(14, zamio.defaultCheckIntervalDays)
        assertTrue(zamio.toxicToPets)

        val monstera = plants.firstOrNull { it.id == "monstera" }
        assertNotNull(monstera)
        assertTrue(monstera!!.nameUk.contains("Монстера"))
        assertEquals(7, monstera.defaultCheckIntervalDays)
    }

    @Test
    fun `CARE_PODS json deserializes all 18 pods with non-null properties`() {
        val file = File("src/main/assets/content/CARE_PODS.json")
        assertTrue("CARE_PODS.json must exist in assets", file.exists())

        val json = file.readText()
        val type = object : TypeToken<List<CarePodEntity>>() {}.type
        val pods: List<CarePodEntity> = gson.fromJson(json, type)

        assertEquals("Care pods should contain exactly 18 cards", 18, pods.size)

        pods.forEach { pod ->
            assertNotNull("id must not be null", pod.id)
            assertTrue("id must not be blank", pod.id.isNotBlank())

            assertNotNull("plantSpeciesId must not be null for ${pod.id}", pod.plantSpeciesId)
            assertTrue("plantSpeciesId must not be blank for ${pod.id}", pod.plantSpeciesId.isNotBlank())

            assertNotNull("category must not be null for ${pod.id}", pod.category)
            assertTrue("category must not be blank for ${pod.id}", pod.category.isNotBlank())

            assertNotNull("titleUk must not be null for ${pod.id}", pod.titleUk)
            assertTrue("titleUk must not be blank for ${pod.id}", pod.titleUk.isNotBlank())

            assertNotNull("contentUk must not be null for ${pod.id}", pod.contentUk)
            assertTrue("contentUk must not be blank for ${pod.id}", pod.contentUk.isNotBlank())

            assertNotNull("icon must not be null for ${pod.id}", pod.icon)
            assertTrue("icon must not be blank for ${pod.id}", pod.icon.isNotBlank())
        }

        // Check general beginner pods
        val generalPods = pods.filter { it.plantSpeciesId == "general_beginner" }
        assertTrue("Should have general beginner pods", generalPods.size >= 4)
        val fingerTestPod = generalPods.find { it.id == "pod_gen_finger_test" }
        assertNotNull(fingerTestPod)
        assertEquals("Тест пальцем на 2 см", fingerTestPod!!.titleUk)
    }
}

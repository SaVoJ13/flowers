package com.flowers.app.domain

import com.flowers.app.data.local.entity.PlantEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SoilCheckAdvisorTest {

    private val tz = TimeZone.getTimeZone("UTC")

    private fun createTimestamp(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance(tz).apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun returnsPersonalAdvice_whenAvailableInPlantEntity() {
        val winterTime = createTimestamp(2026, Calendar.JANUARY, 15)
        val plant = PlantEntity(
            customName = "Мій улюбленець",
            wateringAdvice = "Поливати тільки після повного висихання на третину горщика."
        )

        val advice = SoilCheckAdvisor.getSoilCheckAdvice(plant, winterTime, tz)
        assertTrue(advice.contains("Поливати тільки після повного висихання"))
    }

    @Test
    fun succulentSpecies_returnsFullDryOutAdvice() {
        val springTime = createTimestamp(2026, Calendar.APRIL, 10)
        val plant = PlantEntity(
            customName = "Заміокулькас смарагдовий",
            botanicalName = "Zamioculcas zamiifolia",
            speciesName = "Заміокулькас"
        )

        val advice = SoilCheckAdvisor.getSoilCheckAdvice(plant, springTime, tz)
        assertTrue(advice.contains("Ґрунт має повністю просохнути між перевірками"))
        assertTrue(advice.contains("Уникайте застою води"))
    }

    @Test
    fun aroidSpecies_returnsTopTwoCmDryAdvice() {
        val springTime = createTimestamp(2026, Calendar.APRIL, 10)
        val plant = PlantEntity(
            customName = "Монстера делікатесна",
            botanicalName = "Monstera deliciosa",
            speciesName = "Монстера"
        )

        val advice = SoilCheckAdvisor.getSoilCheckAdvice(plant, springTime, tz)
        assertTrue(advice.contains("Перевірте верхні 2–3 см ґрунту"))
    }

    @Test
    fun moistureLovingSpecies_returnsModerateMoistureAdvice() {
        val summerTime = createTimestamp(2026, Calendar.JULY, 5)
        val plant = PlantEntity(
            customName = "Калатея смугаста",
            botanicalName = "Calathea zebrina",
            speciesName = "Калатея"
        )

        val advice = SoilCheckAdvisor.getSoilCheckAdvice(plant, summerTime, tz)
        assertTrue(advice.contains("Ґрунт має залишатися помірно вологим"))
        assertTrue(advice.contains("Не допускайте пересихання"))
    }

    @Test
    fun unknownSpeciesWithoutAdvice_returnsCautiousManualCheckAdvice() {
        val autumnTime = createTimestamp(2026, Calendar.OCTOBER, 12)
        val plant = PlantEntity(
            customName = "Зелена квітка з ринку",
            botanicalName = null,
            speciesName = null,
            wateringAdvice = null
        )

        val advice = SoilCheckAdvisor.getSoilCheckAdvice(plant, autumnTime, tz)
        assertTrue(advice.contains("Обережно перевірте вологість ґрунту пальцем або дерев'яною шпажкою"))
    }

    @Test
    fun lightLevelDirect_appendsDirectSunNote() {
        val springTime = createTimestamp(2026, Calendar.MAY, 20)
        val plant = PlantEntity(
            customName = "Товстянка на підвіконні",
            botanicalName = "Crassula ovata",
            lightLevel = "DIRECT"
        )

        val advice = SoilCheckAdvisor.getSoilCheckAdvice(plant, springTime, tz)
        assertTrue(advice.contains("На прямому сонці ґрунт випаровує вологу швидше"))
    }

    @Test
    fun lightLevelShade_appendsShadeNote() {
        val springTime = createTimestamp(2026, Calendar.MAY, 20)
        val plant = PlantEntity(
            customName = "Аглаонема в коридорі",
            botanicalName = "Aglaonema commutatum",
            lightLevel = "SHADE"
        )

        val advice = SoilCheckAdvisor.getSoilCheckAdvice(plant, springTime, tz)
        assertTrue(advice.contains("У затінку волога утримується довше"))
    }

    @Test
    fun seasonDetection_identifiesAllFourSeasonsAccurately() {
        assertEquals(SoilCheckAdvisor.Season.WINTER, SoilCheckAdvisor.getSeason(createTimestamp(2026, Calendar.DECEMBER, 15), tz))
        assertEquals(SoilCheckAdvisor.Season.WINTER, SoilCheckAdvisor.getSeason(createTimestamp(2026, Calendar.JANUARY, 10), tz))
        assertEquals(SoilCheckAdvisor.Season.SPRING, SoilCheckAdvisor.getSeason(createTimestamp(2026, Calendar.MARCH, 20), tz))
        assertEquals(SoilCheckAdvisor.Season.SUMMER, SoilCheckAdvisor.getSeason(createTimestamp(2026, Calendar.JULY, 1), tz))
        assertEquals(SoilCheckAdvisor.Season.AUTUMN, SoilCheckAdvisor.getSeason(createTimestamp(2026, Calendar.OCTOBER, 5), tz))
    }
}

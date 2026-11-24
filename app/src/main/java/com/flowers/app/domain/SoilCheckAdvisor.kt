package com.flowers.app.domain

import com.flowers.app.data.local.entity.PlantEntity
import java.util.Calendar
import java.util.TimeZone

object SoilCheckAdvisor {

    enum class Season {
        WINTER, SPRING, SUMMER, AUTUMN
    }

    /**
     * Returns an adaptive soil check advice for the specified plant.
     * Takes into account species advice from Room v3, taxonomy groups, light conditions, and season.
     * If data is insufficient, returns a cautious manual check recommendation.
     */
    fun getSoilCheckAdvice(
        plant: PlantEntity,
        currentDate: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val season = getSeason(currentDate, timeZone)

        // 1. If personalized advice from Room v3 is available, prioritize it
        val personalAdvice = plant.wateringAdvice?.trim()
        if (!personalAdvice.isNullOrBlank()) {
            val seasonalNote = getSeasonalNote(season)
            return if (personalAdvice.contains("зим", ignoreCase = true) ||
                personalAdvice.contains("літ", ignoreCase = true)
            ) {
                personalAdvice
            } else {
                "$personalAdvice $seasonalNote".trim()
            }
        }

        // 2. Identify taxonomic group heuristic
        val combinedText = "${plant.botanicalName.orEmpty()} ${plant.speciesName.orEmpty()} ${plant.customName} ${plant.speciesId.orEmpty()}".lowercase()

        val baseAdvice = when {
            isSucculentOrCactus(combinedText) -> {
                "Ґрунт має повністю просохнути між перевірками. Уникайте застою води в піддоні."
            }
            isAroidOrTropical(combinedText) -> {
                "Перевірте верхні 2–3 см ґрунту: поливайте лише тоді, коли верхній шар підсох."
            }
            isMoistureLoving(combinedText) -> {
                "Ґрунт має залишатися помірно вологим, але без застою води. Не допускайте пересихання."
            }
            else -> {
                "Обережно перевірте вологість ґрунту пальцем або дерев'яною шпажкою перед поливом."
            }
        }

        // 3. Modifier based on light conditions
        val lightModifier = when (plant.lightLevel.uppercase()) {
            "DIRECT" -> " На прямому сонці ґрунт випаровує вологу швидше."
            "SHADE" -> " У затінку волога утримується довше — перевіряйте глибше."
            else -> ""
        }

        // 4. Modifier based on season
        val seasonModifier = " " + getSeasonalNote(season)

        return "$baseAdvice$lightModifier$seasonModifier".trim()
    }

    fun getSeason(currentDate: Long, timeZone: TimeZone = TimeZone.getDefault()): Season {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = currentDate
        }
        return when (calendar.get(Calendar.MONTH)) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> Season.WINTER
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> Season.SPRING
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> Season.SUMMER
            else -> Season.AUTUMN
        }
    }

    private fun getSeasonalNote(season: Season): String {
        return when (season) {
            Season.WINTER -> "(Взимку полив скорочують через повільніший ріст)."
            Season.SUMMER -> "(У літню спеку ґрунт просихає швидше)."
            Season.SPRING -> "(Навесні рослина починає активний ріст)."
            Season.AUTUMN -> "(Восени поступово зменшуйте частоту перевірок)."
        }
    }

    private fun isSucculentOrCactus(text: String): Boolean {
        val keywords = listOf(
            "succulent", "cactus", "cacti", "сукулент", "кактус",
            "zamioculcas", "заміокулькас", "sansevieria", "сансев",
            "crassula", "крассул", "товстян", "aloe", "алое",
            "echeveria", "ехев", "haworthia", "хавор",
            "sedum", "очиток", "mammillaria", "маммілярія",
            "kalanchoe", "каланхое", "euphorbia", "молочай"
        )
        return keywords.any { text.contains(it) }
    }

    private fun isAroidOrTropical(text: String): Boolean {
        val keywords = listOf(
            "monstera", "монстера", "philodendron", "філодендрон",
            "epipremnum", "епіпремнум", "pothos", "потос",
            "spathiphyllum", "спатифілум", "anthurium", "антуріум",
            "syngonium", "сингоніум", "aglaonema", "аглаонема",
            "ficus", "фікус", "dieffenbachia", "дифенбах"
        )
        return keywords.any { text.contains(it) }
    }

    private fun isMoistureLoving(text: String): Boolean {
        val keywords = listOf(
            "fern", "папороть", "nephrolepis", "нефролепіс", "asplenium", "аспленій",
            "calathea", "калатея", "maranta", "маранта", "ctenanthe", "ктенанта",
            "stromanthe", "строманта", "fittonia", "фітонія", "begonia", "бегонія",
            "chlorophytum", "хлорофітум"
        )
        return keywords.any { text.contains(it) }
    }
}

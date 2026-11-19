package com.flowers.app.domain

import com.flowers.app.data.model.SymptomBranch
import com.flowers.app.data.model.SymptomNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymptomCorrelationEngineTest {

    private val testSymptomWithCorrelation = SymptomNode(
        id = "leaves_drooping_wilting",
        name_uk = "Листя поникло",
        context_check = "watering_recency",
        branch_recent_watering_le_3_days = SymptomBranch(
            condition_uk = "Останній полив був 1-3 дні тому",
            probable_cause_uk = "Коренева гниль від надлишку води",
            explanation_uk = "Задуха коренів",
            action_plan_uk = listOf("Припинити полив", "Злити воду")
        ),
        branch_old_watering_gt_7_days = SymptomBranch(
            condition_uk = "Останній полив був > 7 днів тому",
            probable_cause_uk = "Зневоднення",
            explanation_uk = "Брак вологи",
            action_plan_uk = listOf("Полити теплою водою")
        )
    )

    private val testStandardSymptom = SymptomNode(
        id = "leaves_brown_crispy_tips",
        name_uk = "Сухі кінчики",
        probable_cause_uk = "Сухе повітря",
        explanation_uk = "Опалення",
        action_plan_uk = listOf("Зволожити повітря")
    )

    @Test
    fun evaluate_whenWateredYesterday_diagnosesOverwatering() {
        val now = 1700000000000L
        val yesterday = now - 86400000L * 1

        val result = SymptomCorrelationEngine.evaluate(
            symptom = testSymptomWithCorrelation,
            lastWateredTimestamp = yesterday,
            currentTimestamp = now
        )

        assertEquals("Коренева гниль від надлишку води", result.probableCause)
        assertTrue(result.contextNote?.contains("висока ймовірність переливу") == true)
        assertEquals(2, result.actionPlan.size)
    }

    @Test
    fun evaluate_whenWatered10DaysAgo_diagnosesDehydration() {
        val now = 1700000000000L
        val tenDaysAgo = now - 86400000L * 10

        val result = SymptomCorrelationEngine.evaluate(
            symptom = testSymptomWithCorrelation,
            lastWateredTimestamp = tenDaysAgo,
            currentTimestamp = now
        )

        assertEquals("Зневоднення", result.probableCause)
        assertTrue(result.contextNote?.contains("ймовірна нестача води") == true)
        assertEquals(1, result.actionPlan.size)
    }

    @Test
    fun evaluate_standardSymptom_returnsDirectCauseWithoutWateringCheck() {
        val result = SymptomCorrelationEngine.evaluate(
            symptom = testStandardSymptom,
            lastWateredTimestamp = null
        )

        assertEquals("Сухе повітря", result.probableCause)
        assertEquals(null, result.contextNote)
    }
}

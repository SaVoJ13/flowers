package com.flowers.app.domain

import com.flowers.app.data.model.DiagnosisResult
import com.flowers.app.data.model.SymptomNode
import java.util.concurrent.TimeUnit

object SymptomCorrelationEngine {

    /**
     * Correlates the chosen symptom with the timestamp of the last watering event.
     */
    fun evaluate(
        symptom: SymptomNode,
        lastWateredTimestamp: Long?,
        currentTimestamp: Long = System.currentTimeMillis()
    ): DiagnosisResult {
        // If symptom requires context check on recent watering
        if (symptom.context_check == "watering_recency") {
            val daysSinceWatering = if (lastWateredTimestamp != null) {
                val diffMillis = (currentTimestamp - lastWateredTimestamp).coerceAtLeast(0)
                TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
            } else {
                null
            }

            if (daysSinceWatering != null && daysSinceWatering <= 3 && symptom.branch_recent_watering_le_3_days != null) {
                val branch = symptom.branch_recent_watering_le_3_days
                return DiagnosisResult(
                    probableCause = branch.probable_cause_uk,
                    explanation = branch.explanation_uk,
                    actionPlan = branch.action_plan_uk,
                    contextNote = "Останній полив був $daysSinceWatering дн. тому: висока ймовірність переливу!"
                )
            } else if (daysSinceWatering != null && daysSinceWatering >= 7 && symptom.branch_old_watering_gt_7_days != null) {
                val branch = symptom.branch_old_watering_gt_7_days
                return DiagnosisResult(
                    probableCause = branch.probable_cause_uk,
                    explanation = branch.explanation_uk,
                    actionPlan = branch.action_plan_uk,
                    contextNote = "Останній полив був $daysSinceWatering дн. тому: ймовірна нестача води."
                )
            } else if (symptom.branch_recent_watering_le_3_days != null) {
                // Default fallback if intermediate
                val branch = symptom.branch_recent_watering_le_3_days
                return DiagnosisResult(
                    probableCause = branch.probable_cause_uk,
                    explanation = branch.explanation_uk,
                    actionPlan = branch.action_plan_uk,
                    contextNote = "Останній полив: ${daysSinceWatering ?: "невідомо"} дн. тому."
                )
            }
        }

        // Standard symptoms without dynamic watering correlation
        return DiagnosisResult(
            probableCause = symptom.probable_cause_uk ?: "Порушення умов догляду",
            explanation = symptom.explanation_uk ?: "Симптом свідчить про стрес рослини від мікроклімату або освітлення.",
            actionPlan = symptom.action_plan_uk ?: listOf("Огляньте рослину та перевірте вологість ґрунту."),
            contextNote = null
        )
    }
}

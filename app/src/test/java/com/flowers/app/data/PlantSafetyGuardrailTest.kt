package com.flowers.app.data

import com.flowers.app.data.remote.PlantSafetyGuardrail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantSafetyGuardrailTest {

    @Test
    fun `botanicalTermsContainingChlorDoNotTriggerUnsafe`() {
        // Хлорофітум, хлороз, хлорофіл не повинні вважатися отрутохімікатами
        val safeTerms = listOf(
            "Хлорофітум чубатий потребує помірного поливу.",
            "На листі помітний хлороз через дефіцит заліза в ґрунті.",
            "Для синтезу хлорофілу рослині необхідне якісне розсіяне освітлення.",
            "Хлорофитум хохлатый безопасен для кошек.",
            "Для выработки хлорофилла необходим рассеянный свет.",
            "Chlorophytum comosum is safe for pets.",
            "Watch out for signs of iron chlorosis or chlorotic spots."
        )

        assertFalse("Ботанічні терміни не повинні тригерити захисний шар", PlantSafetyGuardrail.containsUnsafeAdvice(safeTerms))
        val (steps, isSanitized) = PlantSafetyGuardrail.sanitizeFirstAidSteps(safeTerms)
        assertFalse(isSanitized)
        assertEquals(safeTerms, steps)
    }

    @Test
    fun `normalWordsContainingGasLettersDoNotTriggerUnsafe`() {
        val safePhrases = listOf(
            "Коли світло згасає ввечері, рослина складає листя.",
            "Інтерес до цвітіння не погасне при правильній підгодівлі.",
            "Листя поступово згасає без регулярного поливу.",
            "Рекомендується окропити листя чистою водою кімнатної температури."
        )

        assertFalse("Слова зі збігом літер не повинні тригерити захисний шар", PlantSafetyGuardrail.containsUnsafeAdvice(safePhrases))
        val (steps, isSanitized) = PlantSafetyGuardrail.sanitizeFirstAidSteps(safePhrases)
        assertFalse(isSanitized)
        assertEquals(safePhrases, steps)
    }

    @Test
    fun `toxicChemicalsUkrainianTriggerUnsafe`() {
        val toxicList = listOf(
            "Обробіть рослину дихлофосом.",
            "Потрібен розчин актари.",
            "Обробіть листя актарою.",
            "Протріть листя розчином хлору.",
            "Використайте хлорку для дезінфекції горщика.",
            "Протріть піддон хлорною водою.",
            "Використайте мідний купорос для обробки.",
            "Обробіть мідним купоросом від грибка.",
            "Приготуйте розчин залізного купоросу.",
            "Обробіть коріння гасом від паразитів.",
            "Полийте окропом для знезараження."
        )

        for (toxic in toxicList) {
            assertTrue("Має визначатися як небезпечна порада: $toxic", PlantSafetyGuardrail.containsUnsafeAdvice(listOf(toxic)))
            val (sanitized, isSanitized) = PlantSafetyGuardrail.sanitizeFirstAidSteps(listOf(toxic))
            assertTrue(isSanitized)
            assertEquals(PlantSafetyGuardrail.SAFE_DEFAULT_FIRST_AID_STEPS, sanitized)
        }
    }

    @Test
    fun `toxicChemicalsRussianTriggerUnsafe`() {
        val toxicRuList = listOf(
            "Обработайте инсектицидом или фунгицидом.",
            "Опрыскайте гербицидом.",
            "Используйте медный купорос или железный купорос.",
            "Опрыскайте медным купоросом или железным купоросом.",
            "Разведите ложку актары.",
            "Промойте корни керосином.",
            "Добавьте белизну в воду для дезинфекции.",
            "Протрите хлоркой все поверхности.",
            "Используйте ядохимикат для опрыскивания.",
            "Обработайте формалином.",
            "Полейте кипятком."
        )

        for (toxic in toxicRuList) {
            assertTrue("Має визначатися як небезпечна порада (RU): $toxic", PlantSafetyGuardrail.containsUnsafeAdvice(listOf(toxic)))
            val (sanitized, isSanitized) = PlantSafetyGuardrail.sanitizeFirstAidSteps(listOf(toxic))
            assertTrue(isSanitized)
            assertEquals(PlantSafetyGuardrail.SAFE_DEFAULT_FIRST_AID_STEPS, sanitized)
        }
    }

    @Test
    fun `toxicChemicalsEnglishTriggerUnsafe`() {
        val toxicEnList = listOf(
            "Spray with pesticide immediately.",
            "Apply insecticide to the stems.",
            "Use bleach solution to wipe leaves.",
            "Treat soil with copper sulfate.",
            "Use kerosene to remove bugs."
        )

        for (toxic in toxicEnList) {
            assertTrue("Має визначатися як небезпечна порада (EN): $toxic", PlantSafetyGuardrail.containsUnsafeAdvice(listOf(toxic)))
            val (sanitized, isSanitized) = PlantSafetyGuardrail.sanitizeFirstAidSteps(listOf(toxic))
            assertTrue(isSanitized)
            assertEquals(PlantSafetyGuardrail.SAFE_DEFAULT_FIRST_AID_STEPS, sanitized)
        }
    }

    @Test
    fun `emptyStepsReturnsSafeDefaults`() {
        val (steps, isSanitized) = PlantSafetyGuardrail.sanitizeFirstAidSteps(emptyList())
        assertTrue(isSanitized)
        assertEquals(PlantSafetyGuardrail.SAFE_DEFAULT_FIRST_AID_STEPS, steps)

        val (stepsBlank, isSanitizedBlank) = PlantSafetyGuardrail.sanitizeFirstAidSteps(listOf("  ", "\n"))
        assertTrue(isSanitizedBlank)
        assertEquals(PlantSafetyGuardrail.SAFE_DEFAULT_FIRST_AID_STEPS, stepsBlank)
    }
}

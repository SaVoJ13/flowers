package com.flowers.app.data.remote

/**
 * Захисний шар для верифікації рекомендацій ШІ щодо догляду та лікування рослин.
 * Запобігає показу токсичних отрутохімікатів, небезпечних хімічних сумішей або шкідливих дій.
 */
object PlantSafetyGuardrail {

    private val UNSAFE_KEYWORDS = listOf(
        // Отрутохімікати та пестициди
        "пестицид", "інсектицид", "фунгіцид", "гербіцид", "отрутохімікат", "хімікат",
        "дихлофос", "актара", "актеллік", "хлор", "білизна", "мідний купорос",
        "залізний купорос", "фундазол", "бордоськ", "марганцівк", "формалін",
        "гас", "бензин", "ацетон", "дихлофос", "фосфамід", "карбофос",
        // English equivalents
        "pesticide", "insecticide", "fungicide", "herbicide", "bleach", "chlorine",
        "dichlorvos", "copper sulfate", "formalin", "kerosene", "petrol", "acetone",
        // Небезпечні суміші та концентрати
        "концентрат", "нерозведений", "окріп", "кип'яток", "сірчана кислота", "соляна кислота"
    )

    val SAFE_DEFAULT_FIRST_AID_STEPS = listOf(
        "Ізолюйте рослину від інших вазонів для запобігання поширенню можливих шкідників або інфекції.",
        "Обережно огляньте листя з обох боків та перевірте вологість ґрунту дерев'яною шпажкою на глибині 3-4 см.",
        "Протріть листя м'якою тканиною, змоченою у чистій воді кімнатної температури, якщо помітно пил або комах.",
        "Забезпечте м'яке розсіяне освітлення та помірну циркуляцію свіжого повітря без протягів.",
        "Зверніться до фахівця або ботанічного центру для безпечного підбору біологічних засобів захисту."
    )

    const val SAFETY_NOTICE_MODIFIED = "Увага: початкові рекомендації містили потенційно небезпечні або токсичні хімічні речовини. Їх було автоматично замінено на безпечні базові заходи до очної консультації з фахівцем."

    /**
     * Перевіряє список рекомендацій на наявність небезпечних речовин чи дій.
     * @return true, якщо виявлено небезпечні рекомендації.
     */
    fun containsUnsafeAdvice(steps: List<String>): Boolean {
        for (step in steps) {
            val lower = step.lowercase()
            for (keyword in UNSAFE_KEYWORDS) {
                if (lower.contains(keyword)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Валідує та повертає безпечний список кроків допомоги.
     * Якщо виявлено небезпечні поради, повертає безпечний нейтральний план та прапорець sanitization.
     */
    fun sanitizeFirstAidSteps(steps: List<String>): Pair<List<String>, Boolean> {
        val validSteps = steps.map { it.trim() }.filter { it.isNotBlank() }
        if (validSteps.isEmpty()) {
            return Pair(SAFE_DEFAULT_FIRST_AID_STEPS, true)
        }

        if (containsUnsafeAdvice(validSteps)) {
            return Pair(SAFE_DEFAULT_FIRST_AID_STEPS, true)
        }

        return Pair(validSteps, false)
    }
}

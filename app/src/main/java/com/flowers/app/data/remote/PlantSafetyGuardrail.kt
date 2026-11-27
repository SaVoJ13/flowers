package com.flowers.app.data.remote

/**
 * Захисний шар для верифікації рекомендацій ШІ щодо догляду та лікування рослин.
 * Запобігає показу токсичних отрутохімікатів, небезпечних хімічних сумішей або шкідливих дій.
 */
object PlantSafetyGuardrail {

    // Безпечні ботанічні терміни, що містять корінь "хлор", але є нормальними біологічними назвами / симптомами
    private val SAFE_BOTANICAL_ROOTS = listOf(
        "хлорофіт", "хлороз", "хлорофіл", "хлоропласт",
        "chlorophyt", "chloros", "chlorophyll", "chloroplast"
    )

    private val UNSAFE_KEYWORDS = listOf(
        // Отрутохімікати та пестициди (UK / RU / EN)
        "пестицид", "інсектицид", "фунгіцид", "гербіцид", "отрутохімікат", "хімікат",
        "дихлофос", "актара", "актеллік", "білизн", "мідний купорос",
        "залізний купорос", "фундазол", "бордоськ", "марганцівк", "формалін",
        "бензин", "ацетон", "фосфамід", "карбофос",
        // Російські токсичні еквіваленти
        "инсектицид", "фунгицид", "гербицид", "отрутохимикат", "химикат",
        "актеллик", "белизн", "медный купорос", "железный купорос",
        "бордосск", "марганцовк", "керосин",
        // English equivalents
        "pesticide", "insecticide", "fungicide", "herbicide", "bleach", "chlorine",
        "dichlorvos", "copper sulfate", "formalin", "kerosene", "petrol", "acetone",
        // Небезпечні суміші та концентрати
        "концентрат", "нерозведений", "нерастворенный", "окріп", "окроп", "кип'яток", "кип'ятк", "кипяток", "кипятк",
        "сірчана кислота", "соляна кислота", "серная кислота", "соляная кислота",
        "sulfuric acid", "hydrochloric acid", "boiling water"
    )

    // Регулярні вирази для слів із короткими коренями, які потребують перевірки меж слів
    private val STANDALONE_UNSAFE_PATTERNS = listOf(
        // "хлор", "хлором", "хлору", "хлорі" (якщо не ботанічний термін)
        Regex("(?<!\\p{L})хлор(?:ом|у|і|а|ний|ная|ное|ні|ная|ный)?(?!\\p{L})", RegexOption.IGNORE_CASE),
        // "гас", "гасом", "гасу", "гасі" (керасин українською; не плутати зі "згасає", "погасне")
        Regex("(?<!\\p{L})гас(?:ом|у|і)?(?!\\p{L})", RegexOption.IGNORE_CASE)
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
            var lower = step.lowercase()

            // 1. Спочатку перевіряємо специфічні патерни (хлор, гас) з виключенням безпечних ботанічних назв
            var textWithoutBotanical = lower
            for (safeRoot in SAFE_BOTANICAL_ROOTS) {
                textWithoutBotanical = textWithoutBotanical.replace(safeRoot, "safe_botanical")
            }

            for (pattern in STANDALONE_UNSAFE_PATTERNS) {
                if (pattern.containsMatchIn(textWithoutBotanical)) {
                    return true
                }
            }

            // 2. Перевіряємо загальні ключові слова
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

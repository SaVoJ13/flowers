package com.flowers.app.data.remote

/**
 * Захисний шар для верифікації рекомендацій ШІ щодо догляду та лікування рослин.
 * Запобігає показу токсичних отрутохімікатів, небезпечних хімічних сумішей або шкідливих дій.
 */
object PlantSafetyGuardrail {

    // Безпечні ботанічні терміни, що містять корінь "хлор", але є нормальними біологічними назвами / симптомами.
    // Використовуємо точні регулярні вирази з межами слів для цілих слів або допустимих словоформ,
    // щоб уникнути неоднозначних часткових збігів (substring matching) та випадкового маскування
    // небезпечних речовин (наприклад, фунгіциду chlorothalonil / хлороталоніл).
    private val SAFE_BOTANICAL_PATTERNS = listOf(
        // English / Latin
        Regex("(?<!\\p{L})chlorotic(?:al|ally)?(?!\\p{L})", RegexOption.IGNORE_CASE),
        Regex("(?<!\\p{L})chloros(?:is|es)(?!\\p{L})", RegexOption.IGNORE_CASE),
        Regex("(?<!\\p{L})chlorophyll(?:s|ic)?(?!\\p{L})", RegexOption.IGNORE_CASE),
        Regex("(?<!\\p{L})chlorophytum(?:s)?(?!\\p{L})", RegexOption.IGNORE_CASE),
        Regex("(?<!\\p{L})chloroplast(?:s|ic)?(?!\\p{L})", RegexOption.IGNORE_CASE),

        // Українські та російські форми
        // Хлороз (хлороз, хлорозу, хлорозом, хлорозі, хлорози, хлорозов, хлорозах...)
        Regex("(?<!\\p{L})хлороз(?:у|ом|і|а|е|ів|ов|ам|ами|ах|и|ы)?(?!\\p{L})", RegexOption.IGNORE_CASE),
        // Хлоротичний / хлоротичный (хлоротичні, хлоротичного, хлоротичные...)
        Regex("(?<!\\p{L})хлоротичн\\p{L}*(?!\\p{L})", RegexOption.IGNORE_CASE),
        // Хлорофіл / хлорофилл (хлорофілу, хлорофилла, хлорофільний...)
        Regex("(?<!\\p{L})хлорофіл\\p{L}*(?!\\p{L})", RegexOption.IGNORE_CASE),
        Regex("(?<!\\p{L})хлорофилл?\\p{L}*(?!\\p{L})", RegexOption.IGNORE_CASE),
        // Хлорофітум / хлорофитум
        Regex("(?<!\\p{L})хлорофітум\\p{L}*(?!\\p{L})", RegexOption.IGNORE_CASE),
        Regex("(?<!\\p{L})хлорофитум\\p{L}*(?!\\p{L})", RegexOption.IGNORE_CASE),
        // Хлоропласт
        Regex("(?<!\\p{L})хлоропласт\\p{L}*(?!\\p{L})", RegexOption.IGNORE_CASE)
    )

    private val UNSAFE_KEYWORDS = listOf(
        // Отрутохімікати та пестициди (UK / RU / EN)
        "пестицид", "інсектицид", "фунгіцид", "гербіцид", "отрутохімікат", "хімікат",
        "дихлофос", "актар", "актеллік", "білизн", "купорос",
        "фундазол", "бордоськ", "марганцівк", "формалін",
        "бензин", "ацетон", "фосфамід", "карбофос",
        "хлороталоніл",
        // Російські токсичні еквіваленти
        "инсектицид", "фунгицид", "гербицид", "отрутохимикат", "химикат", "ядохимикат",
        "актеллик", "белизн", "бордосск", "марганцовк", "керосин", "формалин",
        "хлороталонил",
        // English equivalents
        "pesticide", "insecticide", "fungicide", "herbicide", "bleach",
        "dichlorvos", "copper sulfate", "formalin", "kerosene", "petrol", "acetone",
        "chlorothalonil",
        // Небезпечні суміші та концентрати
        "концентрат", "нерозведений", "нерастворенный",
        "сірчана кислота", "соляна кислота", "серная кислота", "соляная кислота",
        "sulfuric acid", "hydrochloric acid", "boiling water"
    )

    // Регулярні вирази для слів із короткими коренями, які потребують перевірки меж слів
    private val STANDALONE_UNSAFE_PATTERNS = listOf(
        // Будь-яке слово на основі "хлор" (після виключення безпечних ботанічних термінів)
        Regex("(?<!\\p{L})хлор\\p{L}*(?!\\p{L})", RegexOption.IGNORE_CASE),
        Regex("(?<!\\p{L})chlor\\p{L}*(?!\\p{L})", RegexOption.IGNORE_CASE),
        // "гас", "гасом", "гасу", "гасі" (керасин українською; не плутати зі "згасає", "погасне")
        Regex("(?<!\\p{L})гас(?:ом|у|і)?(?!\\p{L})", RegexOption.IGNORE_CASE),
        // "окріп", "окропом", "окропу", "окропі" (не плутати з безпечним українським дієсловом "окропити", "окропіть")
        Regex("(?<!\\p{L})окріп(?!\\p{L})|(?<!\\p{L})окроп(?:ом|у|і|а)?(?!\\p{L})", RegexOption.IGNORE_CASE),
        // "кип'яток", "кипяток" (та відмінювання "кип'ятком", "кипятком", "кип'ятку")
        Regex("(?<!\\p{L})кип'ят(?:ок|ком|ку|ка)?(?!\\p{L})|(?<!\\p{L})кипят(?:ок|ком|ку|ка)?(?!\\p{L})", RegexOption.IGNORE_CASE)
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

            // 1. Спочатку замінюємо безпечні ботанічні терміни на нейтральний маркер за точними патернами
            var textWithoutBotanical = lower
            for (pattern in SAFE_BOTANICAL_PATTERNS) {
                textWithoutBotanical = pattern.replace(textWithoutBotanical, "safe_botanical")
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

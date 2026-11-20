package com.flowers.app.data.report

import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity

/** Причини скарги на відповідь ШІ (TASK-027). Код іде у Firestore, текст показується користувачу. */
enum class ReportReason(val code: String, val label: String) {
    WRONG("wrong", "Неправильна відповідь"),
    DANGEROUS("dangerous", "Небезпечна порада"),
    OFFENSIVE("offensive", "Образливий або недоречний зміст"),
    OTHER("other", "Інше")
}

/**
 * Скарга. Межі довжин ДЗЕРКАЛЯТЬ `firestore.rules`; зміни робити в обох місцях і в тестах правил.
 * Не містить ідентифікаторів користувача, фото, облікових даних.
 */
data class AiReport(
    val plantQuery: String,
    val responseText: String,
    val reason: ReportReason,
    val comment: String,
    val appVersion: String,
    val feature: String = FEATURE_PLANT_CARD
) {
    /** Поля документа Firestore (без `createdAt`: його додає серверний час у репозиторії). */
    fun toFields(): Map<String, Any> = buildMap {
        put("feature", feature)
        put("plantQuery", plantQuery)
        put("responseText", responseText)
        put("reason", reason.code)
        if (comment.isNotEmpty()) put("comment", comment)
        put("appVersion", appVersion)
    }

    companion object {
        const val FEATURE_PLANT_CARD = "plant_card"
        const val MAX_PLANT_QUERY = 100
        const val MAX_RESPONSE = 4000
        const val MAX_COMMENT = 500
        const val MAX_APP_VERSION = 32

        /** Збирає скаргу з ШІ-картки: обрізає під межі правил, прибирає зайві пробіли. */
        fun forPlantCard(
            plant: CatalogPlantEntity,
            pods: List<CarePodEntity>,
            reason: ReportReason,
            comment: String,
            appVersion: String
        ) = AiReport(
            plantQuery = plant.nameUk.trim().take(MAX_PLANT_QUERY).ifEmpty { "?" },
            responseText = cardText(plant, pods).take(MAX_RESPONSE),
            reason = reason,
            comment = comment.trim().take(MAX_COMMENT),
            appVersion = appVersion.take(MAX_APP_VERSION).ifEmpty { "?" }
        )

        internal fun cardText(plant: CatalogPlantEntity, pods: List<CarePodEntity>): String = buildString {
            append("Назва: ").append(plant.nameUk).append(" / ").append(plant.nameLatin)
                .append(" / ").append(plant.popularName).append('\n')
            append("Складність: ").append(plant.difficulty).append('\n')
            append("Світло: ").append(plant.light).append('\n')
            append("Перевірка кожні ").append(plant.defaultCheckIntervalDays).append(" дн.\n")
            append("Ґрунт: ").append(plant.soilType)
            pods.forEach { append('\n').append(it.titleUk).append(": ").append(it.contentUk) }
        }
    }
}

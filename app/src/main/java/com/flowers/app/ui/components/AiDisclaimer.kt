package com.flowers.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.flowers.app.data.local.entity.CatalogPlantEntity

/** Тексти й правила показу ШІ-дисклеймера (TASK-026). */
object AiTexts {
    const val DISCLAIMER = "Порада ШІ, не гарантія. Перевіряйте перед застосуванням."
    const val PRIVACY_HINT =
        "Вводьте лише назву рослини. Не вказуйте особисті дані: назва надсилається в Google для генерації " +
            "(на безкоштовному рівні Google може використовувати запити для покращення своїх продуктів)."
    const val AI_ID_PREFIX = "ai_"

    fun isAiGeneratedId(id: String?): Boolean = id != null && id.startsWith(AI_ID_PREFIX)
}

fun CatalogPlantEntity.isAiGenerated(): Boolean = AiTexts.isAiGeneratedId(id)

@Composable
fun AiDisclaimerText(modifier: Modifier = Modifier) {
    Text(
        text = "🤖 ${AiTexts.DISCLAIMER}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

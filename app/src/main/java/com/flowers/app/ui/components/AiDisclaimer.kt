package com.flowers.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

/** Тексти й правила показу ШІ-дисклеймера (TASK-026). */
object AiTexts {
    const val DISCLAIMER = "Порада ШІ, не гарантія. Перевіряйте перед застосуванням."
    const val PRIVACY_HINT =
        "Вводьте лише назву рослини. Не вказуйте особисті дані: назва надсилається в Google для генерації " +
            "(на безкоштовному рівні Google може використовувати запити для покращення своїх продуктів)."
    const val SAFETY_NOT_VERIFIED =
        "Безпека для тварин і дітей не перевірена. Уточніть у ветеринара або в довіднику ASPCA"
    const val ASPCA_LABEL = "Відкрити довідник ASPCA"
    const val ASPCA_URL = "https://www.aspca.org/pet-care/animal-poison-control/toxic-and-non-toxic-plants"
}

@Composable
fun AiDisclaimerText(modifier: Modifier = Modifier) {
    Text(
        text = "🤖 ${AiTexts.DISCLAIMER}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** Нейтральне повідомлення для ШІ-карток замість токсичності (TASK-025). */
@Composable
fun AiSafetyNotice(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier) {
        Text(
            text = "🐾 ${AiTexts.SAFETY_NOT_VERIFIED}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = { uriHandler.openUri(AiTexts.ASPCA_URL) },
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(AiTexts.ASPCA_LABEL, style = MaterialTheme.typography.labelSmall)
        }
    }
}

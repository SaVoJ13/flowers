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
    const val TOXIC_WARNING = "Увага: токсична для котів та собак при жуванні"
    const val SAFETY_ADVICE =
        "Якщо тварина або дитина з'їла частину рослини, негайно зверніться до ветеринара або лікаря. " +
            "Запишіть назву рослини або сфотографуйте її."
    const val ASPCA_LABEL = "ASPCA (сайт англійською)"
    const val ASPCA_HINT = "Браузер може перекласти сторінку"
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

/**
 * Безпека для тварин і дітей (TASK-025, TASK-046). Дві ситуації, обидві з практичною порадою й посиланням на ASPCA:
 * [toxic] = перевірений прапорець каталогу «токсична»; інакше стан UNKNOWN (ШІ-картка або каталог без джерела).
 * Аналогів ASPCA польською/українською немає, тому посилання підписане «сайт англійською».
 */
@Composable
fun SafetyNotice(toxic: Boolean, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier) {
        if (toxic) {
            Text(
                text = "🐾 ${AiTexts.TOXIC_WARNING}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = "🐾 ${AiTexts.SAFETY_NOT_VERIFIED}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = AiTexts.SAFETY_ADVICE,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = {
                try {
                    uriHandler.openUri(AiTexts.ASPCA_URL)
                } catch (e: Exception) {
                    // Немає браузера: тихо ігноруємо, текст поради лишається видимим.
                }
            },
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(AiTexts.ASPCA_LABEL, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = AiTexts.ASPCA_HINT,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

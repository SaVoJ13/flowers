package com.flowers.app.data.image

import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * Абстракція джерела зображення (TASK-052).
 * Дозволяє безпечно читати потік байтів без зміни оригіналу та підтримувати як Android URI, так і файли чи тестові потоки.
 */
sealed interface ImageSource {
    val displayName: String

    /** Відкриває новий вхідний потік байтів для читання */
    fun openStream(): InputStream

    /** Android Content URI (з галереї або камери) */
    class ContentUri(
        val uri: Uri,
        private val streamProvider: (Uri) -> InputStream?
    ) : ImageSource {
        override val displayName: String = uri.toString()
        override fun openStream(): InputStream =
            streamProvider(uri) ?: throw ImageProcessingException.IoFailure("Неможливо відкрити URI: $uri")
    }

    /** Локальний файл */
    class FromFile(val file: File) : ImageSource {
        override val displayName: String = file.name
        override fun openStream(): InputStream {
            if (!file.exists() || !file.canRead()) {
                throw ImageProcessingException.IoFailure("Файл не існує або недоступний для читання: ${file.absolutePath}")
            }
            return file.inputStream()
        }
    }

    /** Прямий постачальник потоку (для тестів та байтових масивів) */
    class Stream(
        override val displayName: String,
        private val opener: () -> InputStream
    ) : ImageSource {
        override fun openStream(): InputStream = opener()
    }
}

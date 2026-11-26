package com.flowers.app.ui.diagnosis

import java.io.File
import java.util.UUID

/**
 * Сесія захоплення знімка камерою, прив'язана до конкретного цільового файлу
 * та унікального ідентифікатора операції.
 */
data class CameraCaptureSession(
    val file: File,
    val sessionId: Long = System.currentTimeMillis()
)

/**
 * Менеджер життєвого циклу тимчасових файлів камери.
 *
 * Забезпечує:
 * 1. Прив'язку операцій очищення до конкретного файлу та сесії.
 * 2. Захист активної сесії: запізнілий callback попередньої зйомки може видалити
 *    лише власний файл і не має права видаляти файл нової сесії або скидати її стан.
 * 3. Ізоляцію: сторонні файли ніколи не видаляються і не змінюються.
 */
class CameraCaptureManager {

    var activeSession: CameraCaptureSession? = null
        private set

    /**
     * Створює нову сесію з унікальним файлом у вказаній директорії [baseDir].
     * Якщо існувала незавершена попередня сесія, її файл безпечно очищується.
     */
    fun prepareCapture(baseDir: File): CameraCaptureSession {
        cleanUpSession(activeSession)

        val uniqueFileName = "diagnosis_cam_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        val file = File(baseDir, uniqueFileName).apply {
            parentFile?.mkdirs()
        }
        val session = CameraCaptureSession(file = file)
        activeSession = session
        return session
    }

    /**
     * Очищує файл [targetFile].
     * Якщо [targetFile] не збігається з файлом поточної активної сесії [activeSession],
     * видаляється виключно [targetFile], а стан [activeSession] залишається незмінним.
     */
    fun cleanUpFile(targetFile: File?) {
        if (targetFile == null) return
        try {
            if (targetFile.exists() && targetFile.isFile) {
                targetFile.delete()
            }
        } catch (_: Throwable) {}

        if (activeSession?.file == targetFile) {
            activeSession = null
        }
    }

    /**
     * Очищує файл, прив'язаний до вказаної сесії [session].
     */
    fun cleanUpSession(session: CameraCaptureSession?) {
        if (session == null) return
        cleanUpFile(session.file)
    }

    /**
     * Очищує поточну активну сесію (наприклад, при закритті екрана або скасуванні).
     */
    fun cleanUpActiveSession() {
        cleanUpSession(activeSession)
    }
}

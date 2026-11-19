package com.flowers.app.data.remote

/**
 * Єдине місце конфігурації Google Gemini API.
 * Щоб змінити модель — правити лише [GEMINI_MODEL].
 * API-ключ тут НЕ зберігається: користувач вводить його в Налаштуваннях (UserPreferences).
 */
const val GEMINI_MODEL = "gemini-3.5-flash-lite"

const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

/** URL методу generateContent для заданої моделі (за замовчуванням [GEMINI_MODEL]). */
fun geminiGenerateContentUrl(model: String = GEMINI_MODEL): String =
    "$GEMINI_BASE_URL/$model:generateContent"

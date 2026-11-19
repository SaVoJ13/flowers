package com.flowers.app.data.remote

/**
 * Єдине місце конфігурації моделі Google Gemini для Firebase AI Logic.
 * Щоб змінити модель — правити лише [GEMINI_MODEL].
 * Доступ авторизується через Firebase AI Logic та захищається Firebase App Check.
 */
const val GEMINI_MODEL = "gemini-3.5-flash-lite"

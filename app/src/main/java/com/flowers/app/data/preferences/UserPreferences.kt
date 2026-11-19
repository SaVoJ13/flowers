package com.flowers.app.data.preferences

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // TASK-019: Видаляємо раніше збережений локальний API-ключ Gemini, щоб він не лишався на пристроях
        if (prefs.contains(KEY_GEMINI_API_KEY)) {
            prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
        }
    }

    var isPremium: Boolean
        get() = prefs.getBoolean(KEY_IS_PREMIUM, true) // Enabled by default for Owner / test device
        set(value) = prefs.edit().putBoolean(KEY_IS_PREMIUM, value).apply()

    companion object {
        private const val PREFS_NAME = "flowers_user_preferences"
        private const val KEY_IS_PREMIUM = "key_is_premium"
        private const val KEY_GEMINI_API_KEY = "key_gemini_api_key"
    }
}

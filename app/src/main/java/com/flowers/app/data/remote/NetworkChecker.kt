package com.flowers.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Абстракція перевірки мережі перед запитом до ШІ (підміняється в тестах). */
fun interface NetworkChecker {
    fun isOnline(): Boolean
}

/**
 * Є активна мережа з INTERNET і VALIDATED (система підтвердила вихід в інтернет).
 * Потребує normal-дозволу ACCESS_NETWORK_STATE (без запиту користувачу, дані не збираються).
 */
class AndroidNetworkChecker(context: Context) : NetworkChecker {
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    // Сама перевірка може впасти (SecurityException на частині Android 11 тощо): тоді пробуємо запит (рішення Власника).
    override fun isOnline(): Boolean = checkOnlineSafely {
        val manager = cm ?: throw IllegalStateException("ConnectivityManager недоступний")
        val caps = manager.getNetworkCapabilities(manager.activeNetwork ?: return@checkOnlineSafely false)
            ?: return@checkOnlineSafely false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/** Якщо перевірка мережі сама дала збій, повертає true: краще спробувати запит, ніж заблокувати його (TASK-046). */
internal fun checkOnlineSafely(probe: () -> Boolean): Boolean = runCatching(probe).getOrDefault(true)

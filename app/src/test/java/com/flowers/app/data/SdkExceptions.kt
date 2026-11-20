package com.flowers.app.data

/**
 * Створює РЕАЛЬНІ винятки firebase-ai 18.2.0. Їхні конструктори internal у Kotlin (але public у байткоді),
 * тому створюємо через рефлексію; тест лишається чесним щодо типів SDK.
 */
object SdkExceptions {
    private const val PKG = "com.google.firebase.ai.type."

    private fun make(simpleName: String, message: String, cause: Throwable?): Throwable {
        val c = Class.forName(PKG + simpleName).getConstructor(String::class.java, Throwable::class.java)
        return c.newInstance(message, cause) as Throwable
    }

    fun unknown(cause: Throwable?, message: String = "Something unexpected happened.") = make("UnknownException", message, cause)
    fun server(message: String = "Server error", cause: Throwable? = null) = make("ServerException", message, cause)
    fun quota(message: String = "Too many requests", cause: Throwable? = null) = make("QuotaExceededException", message, cause)

    fun requestTimeout(cause: Throwable? = null): Throwable {
        val c = Class.forName(PKG + "RequestTimeoutException")
            .getConstructor(String::class.java, Throwable::class.java, List::class.java)
        return c.newInstance("The request failed to complete in the allotted time.", cause, emptyList<Any>()) as Throwable
    }
}

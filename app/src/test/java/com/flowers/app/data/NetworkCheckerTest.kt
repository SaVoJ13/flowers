package com.flowers.app.data

import com.flowers.app.data.remote.checkOnlineSafely
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Збій самої перевірки мережі не блокує запит до ШІ (рішення Власника 2026-11-20, TASK-046). */
class NetworkCheckerTest {
    @Test
    fun `probe result is passed through`() {
        assertTrue(checkOnlineSafely { true })
        assertFalse(checkOnlineSafely { false })
    }

    @Test
    fun `security exception in probe returns true so the request is attempted`() {
        assertTrue(checkOnlineSafely { throw SecurityException("ACCESS_NETWORK_STATE denied") })
    }

    @Test
    fun `any runtime failure in probe returns true`() {
        assertTrue(checkOnlineSafely { throw IllegalStateException("no connectivity manager") })
    }
}

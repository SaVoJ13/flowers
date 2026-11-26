package com.flowers.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.domain.ReminderCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlantDaoTransactionTest {

    private lateinit var db: FlowersDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlowersDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun newPlant(interval: Int = 7, lastWatered: Long? = null): Long =
        db.plantDao().insertPlant(
            PlantEntity(customName = "Тест", checkIntervalDays = interval, lastWateredDate = lastWatered, nextCheckDate = 1000L)
        )

    private suspend fun events(id: Long) = db.careEventDao().getEventsForPlant(id).first()

    private fun failInsertOf(type: String) {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_insert BEFORE INSERT ON care_events WHEN NEW.eventType = '$type' " +
                "BEGIN SELECT RAISE(ABORT, 'injected failure'); END"
        )
    }

    @Test
    fun water_success_updatesDatesAndLogsEvent() = runBlocking {
        val id = newPlant(interval = 5)
        val now = System.currentTimeMillis()
        assertTrue(db.plantDao().applyWatering(id, now, "n"))
        val p = db.plantDao().getPlantById(id)!!
        assertEquals(now, p.lastWateredDate)
        assertEquals(ReminderCalculator.calculateNextCheckAfterWatering(5, now), p.nextCheckDate)
        assertEquals(listOf("WATER"), events(id).map { it.eventType })
    }

    @Test
    fun water_eventInsertFails_plantChangesRolledBack() = runBlocking {
        val id = newPlant()
        failInsertOf("WATER")
        val failed = runCatching { db.plantDao().applyWatering(id, System.currentTimeMillis(), "n") }
        assertTrue(failed.isFailure)
        val p = db.plantDao().getPlantById(id)!!
        assertNull(p.lastWateredDate)
        assertEquals(1000L, p.nextCheckDate)
        assertTrue(events(id).isEmpty())
    }

    @Test
    fun water_plantUpdateFails_noEventAppears() = runBlocking {
        val id = newPlant()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_update BEFORE UPDATE ON plants BEGIN SELECT RAISE(ABORT, 'injected'); END"
        )
        assertTrue(runCatching { db.plantDao().applyWatering(id, System.currentTimeMillis(), "n") }.isFailure)
        assertTrue(events(id).isEmpty())
    }

    @Test
    fun snooze_success_keepsLastWateredAndLogsDelay() = runBlocking {
        val id = newPlant(lastWatered = 555L)
        val now = System.currentTimeMillis()
        assertTrue(db.plantDao().applySnooze(id, 2, now, "n"))
        val p = db.plantDao().getPlantById(id)!!
        assertEquals(555L, p.lastWateredDate)
        assertEquals(ReminderCalculator.calculateSnoozeDate(2, now), p.nextCheckDate)
        val e = events(id).single()
        assertEquals("DELAY", e.eventType)
        assertEquals(2, e.daysDelayed)
    }

    @Test
    fun snooze_eventInsertFails_rolledBack() = runBlocking {
        val id = newPlant(lastWatered = 555L)
        failInsertOf("DELAY")
        assertTrue(runCatching { db.plantDao().applySnooze(id, 2, System.currentTimeMillis(), "n") }.isFailure)
        assertEquals(1000L, db.plantDao().getPlantById(id)!!.nextCheckDate)
        assertTrue(events(id).isEmpty())
    }

    @Test
    fun skip_success_movesNextCheckByInterval_keepsLastWatered_noLongerOverdue() = runBlocking {
        val id = newPlant(interval = 9, lastWatered = 777L)
        val now = System.currentTimeMillis()
        assertTrue(ReminderCalculator.isDue(db.plantDao().getPlantById(id)!!.nextCheckDate, now))
        assertTrue(db.plantDao().applySkip(id, now, "n"))
        val p = db.plantDao().getPlantById(id)!!
        assertEquals(777L, p.lastWateredDate)
        assertEquals(ReminderCalculator.calculateNextCheckAfterWatering(9, now), p.nextCheckDate)
        assertFalse(ReminderCalculator.isDue(p.nextCheckDate, now))
        assertEquals(listOf("SKIP"), events(id).map { it.eventType })
    }

    @Test
    fun skip_eventInsertFails_rolledBack() = runBlocking {
        val id = newPlant()
        failInsertOf("SKIP")
        assertTrue(runCatching { db.plantDao().applySkip(id, System.currentTimeMillis(), "n") }.isFailure)
        assertEquals(1000L, db.plantDao().getPlantById(id)!!.nextCheckDate)
        assertTrue(events(id).isEmpty())
    }

    @Test
    fun usesFreshStateAndDoesNotOverwriteOtherFields_orMissingPlant() = runBlocking {
        val id = newPlant(interval = 7)
        // concurrent change after a UI object was read: interval and name edited
        val stale = db.plantDao().getPlantById(id)!!
        db.plantDao().updatePlant(stale.copy(customName = "Нова назва", checkIntervalDays = 3))
        val now = System.currentTimeMillis()
        assertTrue(db.plantDao().applyWatering(id, now, "n"))
        val p = db.plantDao().getPlantById(id)!!
        assertEquals("Нова назва", p.customName)
        assertEquals(ReminderCalculator.calculateNextCheckAfterWatering(3, now), p.nextCheckDate)
        assertFalse(db.plantDao().applyWatering(9999L, now, "n"))
    }
}

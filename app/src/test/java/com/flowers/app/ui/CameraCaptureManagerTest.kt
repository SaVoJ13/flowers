package com.flowers.app.ui

import com.flowers.app.ui.diagnosis.CameraCaptureManager
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class CameraCaptureManagerTest {

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(System.getProperty("java.io.tmpdir"), "cam_mgr_test_${UUID.randomUUID()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun delayedCallback_ofPreviousSession_doesNotDeleteOrAffectNewSessionFile() {
        val manager = CameraCaptureManager()

        // 1. Почалася сесія A
        val sessionA = manager.prepareCapture(testDir)
        val fileA = sessionA.file
        val contentA = "PHOTO_A_PAYLOAD_TEST".toByteArray(Charsets.UTF_8)
        fileA.writeBytes(contentA)
        assertTrue(fileA.exists())
        assertEquals(sessionA, manager.activeSession)

        // 2. Користувач скасував або запустив нову сесію B
        val sessionB = manager.prepareCapture(testDir)
        val fileB = sessionB.file
        val contentB = "PHOTO_B_PAYLOAD_TEST_SECURE".toByteArray(Charsets.UTF_8)
        fileB.writeBytes(contentB)
        assertTrue(fileB.exists())
        assertEquals(sessionB, manager.activeSession)

        // 3. Запізнілий callback від операції A завершується і намагається виконати очищення
        manager.cleanUpSession(sessionA)

        // 4. Перевірки:
        // - Файл A видалено
        assertFalse("Файл попередньої сесії A повинен бути видалений", fileA.exists())
        // - Файл B залишається недоторканим і побайтово ідентичним
        assertTrue("Файл нової сесії B повинен залишатися доступним", fileB.exists())
        assertArrayEquals("Вміст файлу сесії B не повинен змінюватися", contentB, fileB.readBytes())
        // - Активна сесія менеджера не скинута і залишається сесією B
        assertNotNull(manager.activeSession)
        assertEquals(sessionB, manager.activeSession)
    }

    @Test
    fun cameraCancelled_cleansUpActiveSessionFileAndResetsState() {
        val manager = CameraCaptureManager()
        val session = manager.prepareCapture(testDir)
        val file = session.file
        file.writeBytes("TEMP_EMPTY_OR_CORRUPT".toByteArray(Charsets.UTF_8))
        assertTrue(file.exists())
        assertEquals(session, manager.activeSession)

        // Системна камера скасована користувачем
        manager.cleanUpSession(session)

        assertFalse("Тимчасовий файл повинен бути видалений", file.exists())
        assertNull("Стан активної сесії має бути скинутий на null", manager.activeSession)
    }

    @Test
    fun screenDisposed_cleansUpActiveSessionFile() {
        val manager = CameraCaptureManager()
        val session = manager.prepareCapture(testDir)
        val file = session.file
        file.writeBytes("DISPOSABLE_TEMP".toByteArray(Charsets.UTF_8))
        assertTrue(file.exists())

        // Екран закривається (onDispose)
        manager.cleanUpActiveSession()

        assertFalse("Файл активної сесії повинен бути видалений при onDispose", file.exists())
        assertNull(manager.activeSession)
    }

    @Test
    fun foreignFiles_inSameDirectory_remainByteForByteUntouched() {
        val manager = CameraCaptureManager()
        val foreignFile = File(testDir, "foreign_user_photo_${UUID.randomUUID()}.jpg")
        val foreignBytes = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A.toByte())
        foreignFile.writeBytes(foreignBytes)

        val session = manager.prepareCapture(testDir)
        session.file.writeBytes("CAPTURE_TEMP".toByteArray(Charsets.UTF_8))

        // Очищення сесії
        manager.cleanUpSession(session)
        assertFalse(session.file.exists())

        // Спроба очистити чужий або довільний файл не чіпає foreignFile
        manager.cleanUpFile(null)

        assertTrue("Сторонній файл повинен існувати", foreignFile.exists())
        assertArrayEquals("Сторонній файл повинен залишатися побайтово незмінним", foreignBytes, foreignFile.readBytes())
    }

    @Test
    fun prepareCapture_safelyCleansUpPreviousUnconfirmedSessionFile() {
        val manager = CameraCaptureManager()
        val session1 = manager.prepareCapture(testDir)
        session1.file.writeBytes("UNCONFIRMED_OLD".toByteArray(Charsets.UTF_8))
        assertTrue(session1.file.exists())

        // Відкриття камери вдруге без завершення першої
        val session2 = manager.prepareCapture(testDir)
        assertFalse("Старий незавершений файл повинен бути автоматично прибраний", session1.file.exists())
        assertTrue(session2.file.parentFile?.exists() == true)
        assertEquals(session2, manager.activeSession)
    }
}

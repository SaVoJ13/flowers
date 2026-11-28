package com.flowers.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flowers.app.data.local.entity.ChatAttachmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO для роботи із графічними вкладеннями повідомлень персонального чату (TASK-056 / Room v5).
 */
@Dao
abstract class ChatAttachmentDao {

    /** Вставка нового вкладення. ABORT для захисту від повторної вставки чи дублікатів. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAttachment(attachment: ChatAttachmentEntity): Long

    /** Пакетна вставка вкладень. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAttachments(attachments: List<ChatAttachmentEntity>): List<Long>

    /** Отримання першого вкладення конкретного повідомлення. */
    @Query("SELECT * FROM chat_attachments WHERE messageId = :messageId LIMIT 1")
    abstract suspend fun getAttachmentForMessage(messageId: Long): ChatAttachmentEntity?

    /** Отримання всіх вкладень конкретного повідомлення. */
    @Query("SELECT * FROM chat_attachments WHERE messageId = :messageId ORDER BY id ASC")
    abstract suspend fun getAttachmentsForMessage(messageId: Long): List<ChatAttachmentEntity>

    /** Отримання вкладень для списку повідомлень. */
    @Query("SELECT * FROM chat_attachments WHERE messageId IN (:messageIds) ORDER BY messageId ASC, id ASC")
    abstract suspend fun getAttachmentsForMessages(messageIds: List<Long>): List<ChatAttachmentEntity>

    /** Усі вкладення конкретної рослини. */
    @Query(
        "SELECT * FROM chat_attachments WHERE messageId IN " +
            "(SELECT id FROM chat_messages WHERE plantId = :plantId) ORDER BY id ASC"
    )
    abstract suspend fun getAttachmentsForPlant(plantId: Long): List<ChatAttachmentEntity>

    /** Усі шляхи файлів фотографій для конкретної рослини (для безпечного очищення файлів на диску). */
    @Query(
        "SELECT filePath FROM chat_attachments WHERE messageId IN " +
            "(SELECT id FROM chat_messages WHERE plantId = :plantId)"
    )
    abstract suspend fun getAllFilePathsForPlant(plantId: Long): List<String>

    /** Усі збережені в базі шляхи файлів (для служби Orphan Cleanup). */
    @Query("SELECT filePath FROM chat_attachments")
    abstract suspend fun getAllStoredAttachmentPaths(): List<String>

    /** Видалення вкладень за id повідомлення. */
    @Query("DELETE FROM chat_attachments WHERE messageId = :messageId")
    abstract suspend fun deleteByMessageId(messageId: Long): Int

    /** Загальна кількість вкладень у базі. */
    @Query("SELECT COUNT(*) FROM chat_attachments")
    abstract suspend fun count(): Int

    /** Отримання вкладення за його primary key id. */
    @Query("SELECT * FROM chat_attachments WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: Long): ChatAttachmentEntity?

    /** Спостереження за списком вкладень повідомлення через Flow. */
    @Query("SELECT * FROM chat_attachments WHERE messageId = :messageId ORDER BY id ASC")
    abstract fun observeAttachmentsForMessage(messageId: Long): Flow<List<ChatAttachmentEntity>>
}

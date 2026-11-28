package com.flowers.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.flowers.app.data.local.entity.ChatMessageEntity
import com.flowers.app.data.local.entity.MessageRole
import com.flowers.app.data.local.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO персонального чату (TASK-056).
 * Усі вибірки фільтруються за plantId; порядок завжди детермінований: timestamp, потім id.
 */
@Dao
abstract class ChatMessageDao {

    /** Повна історія однієї рослини в хронологічному порядку (для UI, працює офлайн). */
    @Query("SELECT * FROM chat_messages WHERE plantId = :plantId ORDER BY timestamp ASC, id ASC")
    abstract fun observeMessages(plantId: Long): Flow<List<ChatMessageEntity>>

    /** Повна історія реплік однієї рослини разом із графічними вкладеннями в хронологічному порядку (Room v5). */
    @Transaction
    @Query("SELECT * FROM chat_messages WHERE plantId = :plantId ORDER BY timestamp ASC, id ASC")
    abstract fun observeMessagesWithAttachments(plantId: Long): Flow<List<com.flowers.app.data.local.entity.ChatMessageWithAttachments>>

    /** Отримання списку реплік із вкладеннями без реактивного спостереження. */
    @Transaction
    @Query("SELECT * FROM chat_messages WHERE plantId = :plantId ORDER BY timestamp ASC, id ASC")
    abstract suspend fun getMessagesWithAttachments(plantId: Long): List<com.flowers.app.data.local.entity.ChatMessageWithAttachments>

    @Query("SELECT * FROM chat_messages WHERE plantId = :plantId ORDER BY timestamp ASC, id ASC")
    abstract suspend fun getMessages(plantId: Long): List<ChatMessageEntity>

    /** Останні [limit] реплік рослини, повернуті в хронологічному порядку (вікно контексту ШІ). */
    @Query(
        "SELECT * FROM (SELECT * FROM chat_messages WHERE plantId = :plantId " +
            "ORDER BY timestamp DESC, id DESC LIMIT :limit) ORDER BY timestamp ASC, id ASC"
    )
    abstract suspend fun getLastMessages(plantId: Long, limit: Int): List<ChatMessageEntity>

    /** Останні [limit] успішно надісланих реплік рослини (статус SENT) у хронологічному порядку. */
    @Query(
        "SELECT * FROM (SELECT * FROM chat_messages WHERE plantId = :plantId AND status = 'SENT' " +
            "ORDER BY timestamp DESC, id DESC LIMIT :limit) ORDER BY timestamp ASC, id ASC"
    )
    abstract suspend fun getLastSentMessages(plantId: Long, limit: Int = 6): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: Long): ChatMessageEntity?

    /**
     * Вставка нової репліки. ABORT (а не REPLACE): повторна вставка з наявним id не мовчки
     * перезаписує репліку і не створює прихованих дублікатів.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMessage(message: ChatMessageEntity): Long

    /** Зміна статусу конкретної репліки. Повертає кількість змінених рядків (0 — id не існує). */
    @Query("UPDATE chat_messages SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    abstract suspend fun updateStatus(id: Long, status: MessageStatus, errorMessage: String?): Int

    /**
     * Початок повтору за тим самим id: ERROR → SENDING. Умова `status = 'ERROR'` робить операцію
     * ідемпотентною: подвійний тап поверне 0 і не запустить другий запит. Нового рядка не створюється.
     */
    @Query("UPDATE chat_messages SET status = 'SENDING', errorMessage = NULL WHERE id = :id AND status = 'ERROR'")
    abstract suspend fun beginRetry(id: Long): Int

    /**
     * Початок повтору за тим самим id із прив'язкою до plantId: ERROR → SENDING.
     * Запобігає випадковому повтору повідомлення іншої рослини.
     */
    @Query("UPDATE chat_messages SET status = 'SENDING', errorMessage = NULL WHERE id = :id AND plantId = :plantId AND status = 'ERROR' AND role = 'USER'")
    abstract suspend fun beginRetryForPlant(id: Long, plantId: Long): Int

    /**
     * Відновлення після перезапуску: репліки, що лишились у SENDING (процес убито посеред запиту),
     * переводяться в ERROR, щоб користувач міг повторити їх за тим самим id. Повертає кількість рядків.
     */
    @Query("UPDATE chat_messages SET status = 'ERROR', errorMessage = :errorMessage WHERE status = 'SENDING'")
    abstract suspend fun markInterruptedSendingAsError(errorMessage: String): Int

    /**
     * Відновлення після перезапуску для конкретної рослини:
     * репліки обраного plantId, що лишились у SENDING, переводяться в ERROR,
     * не зачіпаючи повідомлення інших рослин.
     */
    @Query("UPDATE chat_messages SET status = 'ERROR', errorMessage = :errorMessage WHERE plantId = :plantId AND status = 'SENDING'")
    abstract suspend fun markSendingAsErrorForPlant(plantId: Long, errorMessage: String): Int

    /** Видаляє історію лише обраної рослини. Повертає кількість видалених реплік. */
    @Query("DELETE FROM chat_messages WHERE plantId = :plantId")
    abstract suspend fun deleteAllForPlant(plantId: Long): Int

    /**
     * Атомарне збереження відповіді асистента в одній транзакції Room:
     * переводить повідомлення користувача SENDING -> SENT та вставляє репліку MODEL.
     * Якщо повідомлення користувача відсутнє (видалено рослину або очищено чат)
     * чи вже має статус SENT (захист від повторного callback), операція ідемпотентно повертає -1L.
     */
    @Transaction
    open suspend fun saveAssistantResponse(
        userMessageId: Long,
        modelMessage: ChatMessageEntity
    ): Long {
        val userMsg = getById(userMessageId) ?: return -1L
        if (userMsg.role != MessageRole.USER || userMsg.status != MessageStatus.SENDING) return -1L

        val rows = updateStatus(userMessageId, MessageStatus.SENT, null)
        if (rows <= 0) return -1L

        return insertMessage(
            modelMessage.copy(
                plantId = userMsg.plantId,
                role = MessageRole.MODEL,
                status = MessageStatus.SENT,
                errorMessage = null
            )
        )
    }
}

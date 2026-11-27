package com.flowers.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flowers.app.data.local.entity.ChatMessageEntity
import com.flowers.app.data.local.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO персонального чату (TASK-056, крок 1: лише збереження, без мережі й UI).
 * Усі вибірки фільтруються за plantId; порядок завжди детермінований: timestamp, потім id.
 */
@Dao
interface ChatMessageDao {

    /** Повна історія однієї рослини в хронологічному порядку (для UI, працює офлайн). */
    @Query("SELECT * FROM chat_messages WHERE plantId = :plantId ORDER BY timestamp ASC, id ASC")
    fun observeMessages(plantId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE plantId = :plantId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessages(plantId: Long): List<ChatMessageEntity>

    /** Останні [limit] реплік рослини, повернуті в хронологічному порядку (вікно контексту ШІ). */
    @Query(
        "SELECT * FROM (SELECT * FROM chat_messages WHERE plantId = :plantId " +
            "ORDER BY timestamp DESC, id DESC LIMIT :limit) ORDER BY timestamp ASC, id ASC"
    )
    suspend fun getLastMessages(plantId: Long, limit: Int): List<ChatMessageEntity>

    /** Останні [limit] успішно надісланих реплік рослини (статус SENT) у хронологічному порядку. */
    @Query(
        "SELECT * FROM (SELECT * FROM chat_messages WHERE plantId = :plantId AND status = 'SENT' " +
            "ORDER BY timestamp DESC, id DESC LIMIT :limit) ORDER BY timestamp ASC, id ASC"
    )
    suspend fun getLastSentMessages(plantId: Long, limit: Int = 6): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ChatMessageEntity?

    /**
     * Вставка нової репліки. ABORT (а не REPLACE): повторна вставка з наявним id не мовчки
     * перезаписує репліку і не створює прихованих дублікатів.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    /** Зміна статусу конкретної репліки. Повертає кількість змінених рядків (0 — id не існує). */
    @Query("UPDATE chat_messages SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: MessageStatus, errorMessage: String?): Int

    /**
     * Початок повтору за тим самим id: ERROR → SENDING. Умова `status = 'ERROR'` робить операцію
     * ідемпотентною: подвійний тап поверне 0 і не запустить другий запит. Нового рядка не створюється.
     */
    @Query("UPDATE chat_messages SET status = 'SENDING', errorMessage = NULL WHERE id = :id AND status = 'ERROR'")
    suspend fun beginRetry(id: Long): Int

    /**
     * Відновлення після перезапуску: репліки, що лишились у SENDING (процес убито посеред запиту),
     * переводяться в ERROR, щоб користувач міг повторити їх за тим самим id. Повертає кількість рядків.
     */
    @Query("UPDATE chat_messages SET status = 'ERROR', errorMessage = :errorMessage WHERE status = 'SENDING'")
    suspend fun markInterruptedSendingAsError(errorMessage: String): Int

    /** Видаляє історію лише обраної рослини. Повертає кількість видалених реплік. */
    @Query("DELETE FROM chat_messages WHERE plantId = :plantId")
    suspend fun deleteAllForPlant(plantId: Long): Int
}

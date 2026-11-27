package com.flowers.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Автор репліки чату. У БД зберігається рядком `name` (див. ChatConverters). */
enum class MessageRole { USER, MODEL }

/**
 * Статус репліки.
 * SENDING — збережена локально, мережевий запит ще триває/не завершився;
 * SENT — доставлена (для USER: ШІ відповів; для MODEL: завжди SENT);
 * ERROR — відправка невдала, можна повторити за тим самим id.
 */
enum class MessageStatus { SENDING, SENT, ERROR }

/**
 * Репліка персонального чату конкретної рослини (TASK-056, Room v4).
 *
 * Історія кожної рослини ізольована через `plantId`; видалення рослини каскадно видаляє лише її чат.
 * Єдиний складений індекс (plantId, timestamp) обслуговує і фільтр за рослиною, і сортування;
 * детермінований порядок при однакових timestamp забезпечується додатковим ORDER BY id.
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = PlantEntity::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["plantId", "timestamp"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus = MessageStatus.SENT,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

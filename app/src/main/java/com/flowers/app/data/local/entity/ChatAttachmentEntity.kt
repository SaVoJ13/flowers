package com.flowers.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Графічне вкладення (фотографія) репліки персонального чату рослини (TASK-056 / PHOTO ARCHITECTURE, Room v5).
 *
 * Прив'язане до [ChatMessageEntity] через [messageId].
 * Каскадне видалення: при видаленні репліки або рослини SQLite автоматично видаляє відповідні записи вкладень.
 */
@Entity(
    tableName = "chat_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class ChatAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val filePath: String,
    val mimeType: String = "image/jpeg",
    val fileSizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

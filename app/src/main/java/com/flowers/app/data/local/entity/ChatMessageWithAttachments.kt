package com.flowers.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Агрегована модель репліки персонального чату разом із її вкладеннями (Room v5).
 */
data class ChatMessageWithAttachments(
    @Embedded val message: ChatMessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "messageId"
    )
    val attachments: List<ChatAttachmentEntity> = emptyList()
) {
    /** Перше вкладення-фотографія для початкового MVP (максимум одне фото на повідомлення). */
    val primaryPhoto: ChatAttachmentEntity? get() = attachments.firstOrNull()
}

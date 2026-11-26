package com.flowers.app.data.local

import androidx.room.TypeConverter
import com.flowers.app.data.local.entity.MessageRole
import com.flowers.app.data.local.entity.MessageStatus

/**
 * Стабільне збереження enum чату рядками `name`.
 * Збереження за назвою (а не порядковим номером) захищає дані від зміни порядку констант.
 */
class ChatConverters {
    @TypeConverter
    fun roleToString(role: MessageRole): String = role.name

    @TypeConverter
    fun stringToRole(value: String): MessageRole = MessageRole.valueOf(value)

    @TypeConverter
    fun statusToString(status: MessageStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): MessageStatus = MessageStatus.valueOf(value)
}

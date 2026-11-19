package com.flowers.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "care_events",
    foreignKeys = [
        ForeignKey(
            entity = PlantEntity::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["plantId"]), Index(value = ["timestamp"])]
)
data class CareEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val eventType: String, // "WATER", "DELAY", "SKIP", "CHECK_STATE", "REPOT", "FERTILIZE"
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val photoUri: String? = null,
    val daysDelayed: Int? = null
)

package com.flowers.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plants")
data class PlantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customName: String,
    val speciesId: String? = null,
    val roomLocation: String = "Вітальня",
    val lightLevel: String = "INDIRECT", // "SHADE", "INDIRECT", "DIRECT"
    val checkIntervalDays: Int = 7,
    val lastWateredDate: Long? = null,
    val nextCheckDate: Long = System.currentTimeMillis() + 7 * 86400000L,
    val photoUri: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

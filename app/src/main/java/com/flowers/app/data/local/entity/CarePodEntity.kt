package com.flowers.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "care_pods")
data class CarePodEntity(
    @PrimaryKey val id: String,
    val plantSpeciesId: String, // "general_beginner" or specific plant id
    val category: String,      // "moisture_check", "light_placement", "common_mistake", "general_rule"
    val titleUk: String,
    val contentUk: String,
    val icon: String
)

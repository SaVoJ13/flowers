package com.flowers.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_plants")
data class CatalogPlantEntity(
    @PrimaryKey val id: String,
    val nameUk: String,
    val nameLatin: String,
    val popularName: String,
    val difficulty: String,
    val light: String,
    val defaultCheckIntervalDays: Int,
    val soilType: String,
    val toxicToPets: Boolean
)

package com.flowers.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "catalog_plants")
data class CatalogPlantEntity(
    @PrimaryKey
    @SerializedName("id")
    val id: String,

    @SerializedName("name_uk")
    val nameUk: String,

    @SerializedName("name_latin")
    val nameLatin: String,

    @SerializedName("popular_name")
    val popularName: String,

    @SerializedName("difficulty")
    val difficulty: String,

    @SerializedName("light")
    val light: String,

    @SerializedName("default_check_interval_days")
    val defaultCheckIntervalDays: Int,

    @SerializedName("soil_type")
    val soilType: String,

    @SerializedName("toxic_to_pets")
    val toxicToPets: Boolean
)

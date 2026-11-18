package com.flowers.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "care_pods")
data class CarePodEntity(
    @PrimaryKey
    @SerializedName("id")
    val id: String,

    @SerializedName("plant_id")
    val plantSpeciesId: String, // "general_beginner" or specific plant id

    @SerializedName("category")
    val category: String,      // "moisture_check", "light_placement", "common_mistake", "general_rule"

    @SerializedName("title_uk")
    val titleUk: String,

    @SerializedName("content_uk")
    val contentUk: String,

    @SerializedName("icon")
    val icon: String
)

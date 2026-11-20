package com.flowers.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flowers.app.data.local.dao.CareEventDao
import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.local.dao.PlantDao
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        PlantEntity::class,
        CareEventEntity::class,
        CatalogPlantEntity::class,
        CarePodEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class FlowersDatabase : RoomDatabase() {

    abstract fun plantDao(): PlantDao
    abstract fun careEventDao(): CareEventDao
    abstract fun catalogDao(): CatalogDao
    abstract fun carePodDao(): CarePodDao

    companion object {
        @Volatile
        private var INSTANCE: FlowersDatabase? = null

        fun getInstance(context: Context): FlowersDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FlowersDatabase::class.java,
                    "flowers_database.db"
                )
                    .addMigrations(Migrations.MIGRATION_1_2)
                    .addCallback(PrepopulateCallback(context.applicationContext))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class PrepopulateCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            populateIfEmpty(context)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            populateIfEmpty(context)
        }

        private fun populateIfEmpty(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = getInstance(context)
                    val gson = Gson()

                    // 1. Prepopulate Catalog Plants if empty
                    if (database.catalogDao().count() == 0) {
                        val catalogJson = context.assets.open("content/PLANT_CATALOG.json")
                            .bufferedReader().use { it.readText() }
                        val catalogType = object : TypeToken<List<CatalogPlantEntity>>() {}.type
                        val catalogList: List<CatalogPlantEntity> = gson.fromJson(catalogJson, catalogType)
                        database.catalogDao().insertAll(catalogList)
                    }

                    // 2. Prepopulate Care Pods if empty
                    if (database.carePodDao().count() == 0) {
                        val podsJson = context.assets.open("content/CARE_PODS.json")
                            .bufferedReader().use { it.readText() }
                        val podsType = object : TypeToken<List<CarePodEntity>>() {}.type
                        val podsList: List<CarePodEntity> = gson.fromJson(podsJson, podsType)
                        database.carePodDao().insertAll(podsList)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FlowersDatabase", "Failed to populate database", e)
                }
            }
        }
    }
}

package com.flowers.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flowers.app.data.local.entity.CatalogPlantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_plants ORDER BY nameUk ASC")
    fun getAllCatalogPlants(): Flow<List<CatalogPlantEntity>>

    @Query("SELECT * FROM catalog_plants WHERE id = :id LIMIT 1")
    suspend fun getCatalogPlantById(id: String): CatalogPlantEntity?

    @Query("SELECT * FROM catalog_plants WHERE nameUk LIKE '%' || :query || '%' OR nameLatin LIKE '%' || :query || '%' OR popularName LIKE '%' || :query || '%'")
    fun searchCatalog(query: String): Flow<List<CatalogPlantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plants: List<CatalogPlantEntity>)

    @Query("SELECT COUNT(*) FROM catalog_plants")
    suspend fun count(): Int
}

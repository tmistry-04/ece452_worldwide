package com.example.pantryparty.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {
    /** Emits the full list and re-emits automatically whenever the table changes. */
    @Query("SELECT * FROM pantry_items ORDER BY name")
    fun observeAll(): Flow<List<PantryItem>>

    /** One-shot snapshot of the whole pantry (used by the recipe recommender). */
    @Query("SELECT * FROM pantry_items ORDER BY name")
    suspend fun getAll(): List<PantryItem>

    /** The existing row for an ingredient, if any — used to merge on add. */
    @Query("SELECT * FROM pantry_items WHERE spoonacularId = :spoonacularId LIMIT 1")
    suspend fun findBySpoonacularId(spoonacularId: Int): PantryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PantryItem)

    /** Updates an existing row in place (e.g. after a quantity change). */
    @Update
    suspend fun update(item: PantryItem)

    @Delete
    suspend fun delete(item: PantryItem)
}

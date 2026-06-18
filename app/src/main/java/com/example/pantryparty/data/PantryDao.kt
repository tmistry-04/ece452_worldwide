package com.example.pantryparty.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {
    /** Emits the full list and re-emits automatically whenever the table changes. */
    @Query("SELECT * FROM pantry_items ORDER BY name")
    fun observeAll(): Flow<List<PantryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PantryItem)

    @Delete
    suspend fun delete(item: PantryItem)
}

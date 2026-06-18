package com.example.pantryparty.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PantryItem::class], version = 1, exportSchema = false)
abstract class PantryDatabase : RoomDatabase() {
    abstract fun pantryDao(): PantryDao

    companion object {
        @Volatile
        private var INSTANCE: PantryDatabase? = null

        /** Single shared database instance for the whole process. */
        fun getInstance(context: Context): PantryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PantryDatabase::class.java,
                    "pantry.db"
                ).build().also { INSTANCE = it }
            }
    }
}

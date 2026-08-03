package com.example.pantryparty.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// v7: pantry overhaul — the single pantry_items table is replaced by a catalog
// of desired items (catalog_items) plus a purchase/usage ledger
// (pantry_transactions) that stock is derived from.
// Destructive migration is configured below, so the bump just resets local data —
// no hand-written migration needed.
@Database(
    entities = [CatalogItem::class, PantryTransaction::class],
    version = 7,
    exportSchema = false
)
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
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
    }
}

package com.example.pantryparty.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single item stored in the pantry. One row per ingredient: the unique index on
 * [spoonacularId] guarantees an ingredient can't be tracked as two separate rows
 * (the add flow merges into the existing row instead).
 */
@Entity(
    tableName = "pantry_items",
    indices = [Index(value = ["spoonacularId"], unique = true)]
)
data class PantryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Int,
    val unit: String = "piece",        // unit chosen from Spoonacular's possibleUnits
    val spoonacularId: Int,            // ingredient id from Spoonacular; always set via autocomplete
    val expiryDate: Long? = null,      // epoch millis, optional
    val imageUrl: String? = null       // Spoonacular image filename (e.g. "apple.jpg"); used for thumbnails
)

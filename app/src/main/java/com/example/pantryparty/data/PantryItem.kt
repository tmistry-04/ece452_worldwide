package com.example.pantryparty.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A single item stored in the pantry. One row per item. */
@Entity(tableName = "pantry_items")
data class PantryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Int,
    val unit: String = "piece",        // unit chosen from Spoonacular's possibleUnits
    val spoonacularId: Int,            // ingredient id from Spoonacular; always set via autocomplete
    val expiryDate: Long? = null       // epoch millis, optional
)

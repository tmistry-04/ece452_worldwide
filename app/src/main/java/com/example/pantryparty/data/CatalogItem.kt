package com.example.pantryparty.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One ingredient the household wants to keep stocked. The catalog describes the
 * *target* state of the pantry ([desiredAmount] in [unit]); what is actually on
 * hand is derived from the [PantryTransaction] ledger, never stored here.
 *
 * The unique index on [spoonacularId] guarantees an ingredient can't be tracked
 * as two separate rows (the add flow merges into the existing row instead).
 */
@Entity(
    tableName = "catalog_items",
    indices = [Index(value = ["spoonacularId"], unique = true)]
)
data class CatalogItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val desiredAmount: Int,
    val spoonacularId: Int,            // ingredient id from Spoonacular; set via autocomplete
    val imageUrl: String? = null,      // Spoonacular image filename (e.g. "apple.jpg")
    val sortOrder: Int,                // manual list position, rearranged in edit mode
    val unit: String,                  // from Spoonacular's possibleUnits, or user-typed custom
    val aisle: String? = null          // store aisle from Spoonacular (e.g. "Produce")
)

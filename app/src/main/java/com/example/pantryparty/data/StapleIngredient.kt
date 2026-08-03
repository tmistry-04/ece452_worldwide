package com.example.pantryparty.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An ingredient the user has declared they always have on hand — the "Always have"
 * list. Staples are skipped by the recipe amount check and by the deduction plan, so
 * they never read as missing and never get deducted when you cook.
 *
 * This is deliberately not a [CatalogItem]: salt, oil and water are things you never
 * count, so forcing them through the catalog would mean inventing a desired amount, a
 * unit and an expiry for each one.
 *
 * [spoonacularId] is the primary key, so re-adding the same ingredient replaces the
 * row instead of duplicating it. [name] is kept because matching falls back to it —
 * see StapleSet in RecipeMatcher.kt.
 */
@Entity(tableName = "staple_ingredients")
data class StapleIngredient(
    @PrimaryKey val spoonacularId: Int,
    val name: String
)

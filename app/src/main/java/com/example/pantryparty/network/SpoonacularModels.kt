package com.example.pantryparty.network

import kotlinx.serialization.Serializable

// All optional fields carry defaults so a missing key in the API response falls
// back safely instead of failing to parse (SpoonacularJson also ignores unknown
// keys, so new response fields can't break us either).

/** One suggestion from the autocomplete endpoint (metaInformation=true). */
@Serializable
data class IngredientAutocomplete(
    val id: Int,
    val name: String,
    val image: String? = null,
    val aisle: String? = null,
    val possibleUnits: List<String> = emptyList()
)

// ---------------------------------------------------------------------------
// Recipe endpoints
// ---------------------------------------------------------------------------

/**
 * Envelope for complexSearch. With `fillIngredients=true` each result carries the
 * same used/missed ingredient split findByIngredients returned, so the results
 * reuse [RecipeByIngredient] (verified against the live API).
 */
@Serializable
data class ComplexSearchResponse(
    val results: List<RecipeByIngredient> = emptyList(),
    val totalResults: Int = 0
)

/** One recipe from a search — counts are presence-based (no amounts). */
@Serializable
data class RecipeByIngredient(
    val id: Int,
    val title: String,
    val image: String? = null,
    val usedIngredientCount: Int = 0,
    val missedIngredientCount: Int = 0,
    val usedIngredients: List<RecipeIngredientBrief> = emptyList(),
    val missedIngredients: List<RecipeIngredientBrief> = emptyList()
)

/** Lightweight ingredient shape used inside findByIngredients results. */
@Serializable
data class RecipeIngredientBrief(
    val id: Int,
    val name: String,
    val amount: Double = 0.0,
    val unit: String = "",
    val original: String? = null,
    val image: String? = null
)

/** Full recipe details from informationBulk — carries required amounts. */
@Serializable
data class RecipeInformation(
    val id: Int,
    val title: String,
    val image: String? = null,
    val readyInMinutes: Int? = null,
    val servings: Int? = null,
    val extendedIngredients: List<ExtendedIngredient> = emptyList()
)

/**
 * A required ingredient with its amount/unit.
 *
 * `id` is *not* reliably the same id `food/ingredients/autocomplete` gives a pantry
 * row: a recipe may say 1145 ("unsalted butter") where autocomplete said 1001
 * ("butter"). `name` is the canonical name and agrees across both endpoints, so
 * matching falls back to it — see PantryIndex in RecipeMatcher.kt.
 */
@Serializable
data class ExtendedIngredient(
    val id: Int,
    val name: String,
    val amount: Double = 0.0,
    val unit: String = "",
    val original: String? = null,
    val measures: Measures? = null
)

@Serializable
data class Measures(
    val metric: Measure? = null,
    val us: Measure? = null
)

@Serializable
data class Measure(
    val amount: Double,
    val unitShort: String = "",
    val unitLong: String = ""
)

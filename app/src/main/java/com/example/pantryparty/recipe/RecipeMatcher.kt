package com.example.pantryparty.recipe

import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.ExtendedIngredient
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.RecipeInformation

/** An ingredient the user has, linked to the pantry row that satisfies it. */
data class MatchedIngredient(
    val required: ExtendedIngredient,
    val pantryItem: PantryItem
)

/** An ingredient the user lacks (absent) or doesn't have enough of (short). */
data class MissingIngredient(
    val required: ExtendedIngredient,
    val haveQuantity: Int?,   // null when the ingredient is absent entirely
    val haveUnit: String?
)

/** Result of comparing one recipe against the pantry. */
data class RecipeMatch(
    val recipe: RecipeInformation,
    val available: List<MatchedIngredient>,
    val missing: List<MissingIngredient>
) {
    val missingCount: Int get() = missing.size
    val canMake: Boolean get() = missing.isEmpty()
}

/**
 * Pure pantry-vs-recipe comparison. No Android dependencies so it is unit-testable.
 *
 * Sufficiency rule (PoC): match by Spoonacular ingredient id. When the pantry unit
 * and the recipe unit agree, compare amounts; when they differ, fall back to
 * presence (treat the ingredient as available). No unit conversion is attempted.
 */
object RecipeMatcher {

    /** Max missing ingredients a recipe may have to still be worth showing. */
    const val MAX_MISSING = 3

    fun match(pantry: List<PantryItem>, recipe: RecipeInformation): RecipeMatch {
        // Index pantry by Spoonacular id for O(1) lookups per ingredient.
        val byId = pantry.associateBy { it.spoonacularId }

        val available = mutableListOf<MatchedIngredient>()
        val missing = mutableListOf<MissingIngredient>()

        for (required in recipe.extendedIngredients) {
            val have = byId[required.id]
            when {
                // Not in the pantry at all -> missing.
                have == null ->
                    missing += MissingIngredient(required, haveQuantity = null, haveUnit = null)

                // Same unit: we can actually compare amounts.
                unitsMatch(have.unit, required.unit) ->
                    if (have.quantity >= required.amount) {
                        available += MatchedIngredient(required, have)
                    } else {
                        missing += MissingIngredient(required, have.quantity, have.unit)
                    }

                // Units differ: no conversion in the PoC, so treat as available.
                else ->
                    available += MatchedIngredient(required, have)
            }
        }

        return RecipeMatch(recipe, available, missing)
    }

    /** Keeps recipes within [MAX_MISSING] and sorts fewest-missing first. */
    fun bucketAndSort(matches: List<RecipeMatch>): List<RecipeMatch> =
        matches.filter { it.missingCount <= MAX_MISSING }
            .sortedBy { it.missingCount }

    /**
     * Recommender bucketing straight off findByIngredients — uses Spoonacular's
     * own `missedIngredientCount` (staples excluded via ignorePantry), so it
     * needs no extra API calls. Keeps recipes within [MAX_MISSING], fewest first.
     */
    fun bucketByMissed(recipes: List<RecipeByIngredient>): List<RecipeByIngredient> =
        recipes.filter { it.missedIngredientCount <= MAX_MISSING }
            .sortedBy { it.missedIngredientCount }

    private fun unitsMatch(a: String?, b: String?): Boolean {
        // Treat blank/"piece" loosely; otherwise compare normalized text.
        val na = a?.trim()?.lowercase().orEmpty()
        val nb = b?.trim()?.lowercase().orEmpty()
        return na.isNotEmpty() && na == nb
    }
}

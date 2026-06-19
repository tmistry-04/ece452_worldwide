package com.example.pantryparty.recipe

import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.RecipeInformation
import kotlin.math.floor

/**
 * What a "I made this recipe" action will do to the pantry.
 *
 * @param toUpdate pantry rows whose quantity drops but stays above zero.
 * @param toDelete pantry rows fully consumed (quantity hit zero) — removed.
 * @param skipped  ingredient names left untouched (not in pantry, or the units
 *                 don't match so no safe deduction is possible).
 */
data class ConsumeResult(
    val toUpdate: List<PantryItem>,
    val toDelete: List<PantryItem>,
    val skipped: List<String>
)

/**
 * Pure, Android-free deduction logic (testable like [RecipeMatcher]).
 *
 * Policy (confirmed with the user): only deduct when the pantry unit matches
 * the recipe unit. When units differ — or the ingredient isn't in the pantry —
 * we never guess a conversion; that ingredient is reported as `skipped` and
 * left exactly as-is.
 */
object PantryConsumer {

    fun consume(pantry: List<PantryItem>, recipe: RecipeInformation): ConsumeResult {
        // Index the pantry by Spoonacular id for O(1) lookups per ingredient.
        val byId = pantry.associateBy { it.spoonacularId }

        val toUpdate = mutableListOf<PantryItem>()
        val toDelete = mutableListOf<PantryItem>()
        val skipped = mutableListOf<String>()

        for (required in recipe.extendedIngredients) {
            val have = byId[required.id]
            when {
                // Not in the pantry, or units differ -> can't safely deduct.
                have == null || !RecipeMatcher.unitsMatch(have.unit, required.unit) ->
                    skipped += required.name

                // Same unit: subtract the required amount (floored to whole units),
                // never below zero. Reaching zero removes the row.
                else -> {
                    val remaining = floor(have.quantity - required.amount)
                        .toInt()
                        .coerceAtLeast(0)
                    if (remaining == 0) {
                        toDelete += have
                    } else {
                        toUpdate += have.copy(quantity = remaining)
                    }
                }
            }
        }

        return ConsumeResult(toUpdate, toDelete, skipped)
    }
}

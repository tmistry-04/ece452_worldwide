package com.example.pantryparty.recipe

import com.example.pantryparty.pantry.StockItem
import com.example.pantryparty.network.ExtendedIngredient
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.RecipeInformation

/** An ingredient the user has, linked to the pantry row that satisfies it. */
data class MatchedIngredient(
    val required: ExtendedIngredient,
    val pantryItem: StockItem
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

    /**
     * @param nonStapleIds the recipe's non-staple ingredient ids (the search's
     * used∪missed set). Required ingredients whose id is absent from this set are
     * pantry staples that `ignorePantry` assumes you have — they are skipped here
     * so they never count as missing ("short on"); the card shows them on their
     * own via [staplesOf]. Pass null to check every ingredient.
     */
    fun match(
        pantry: List<StockItem>,
        recipe: RecipeInformation,
        nonStapleIds: Set<Int>? = null
    ): RecipeMatch {
        // Index pantry by Spoonacular id for O(1) lookups per ingredient.
        val byId = indexByIngredient(pantry)

        val available = mutableListOf<MatchedIngredient>()
        val missing = mutableListOf<MissingIngredient>()

        for (required in recipe.extendedIngredients) {
            // Staple (assumed on hand): not part of the amount check at all.
            if (nonStapleIds != null && required.id !in nonStapleIds) continue

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

    /**
     * The recipe's staple ingredients — the ones `ignorePantry` assumes you have,
     * i.e. required ingredients whose id is absent from [nonStapleIds] (the
     * search's used∪missed set). Surfaced as their own list on the recipe card.
     */
    fun staplesOf(recipe: RecipeInformation, nonStapleIds: Set<Int>): List<ExtendedIngredient> =
        recipe.extendedIngredients.filter { it.id !in nonStapleIds }

    /**
     * Recommender bucketing straight off findByIngredients — needs no extra API
     * calls. Counts off the `missedIngredients` list (not the `missedIngredientCount`
     * scalar) so filtering, sorting, and the card's badge/pills all agree on a
     * single source of truth. Keeps recipes within [MAX_MISSING], fewest first.
     */
    fun bucketByMissed(recipes: List<RecipeByIngredient>): List<RecipeByIngredient> =
        recipes.filter { it.missedIngredients.size <= MAX_MISSING }
            .sortedBy { it.missedIngredients.size }

    /**
     * Indexes the pantry by Spoonacular id. The DB enforces one row per ingredient,
     * so this is normally a 1:1 map — but it is defensive against duplicate rows:
     * same-unit duplicates have their quantities summed; if the units disagree there
     * is no safe way to combine, so the first row wins. Shared with [PantryConsumer].
     */
    internal fun indexByIngredient(pantry: List<StockItem>): Map<Int, StockItem> =
        pantry.groupBy { it.spoonacularId }.mapValues { (_, rows) ->
            rows.reduce { acc, row ->
                if (unitsMatch(acc.unit, row.unit)) acc.copy(quantity = acc.quantity + row.quantity)
                else acc
            }
        }

    /**
     * Shared unit-equality rule, also reused by [PantryConsumer]: case- and
     * whitespace-insensitive equality. A blank unit never matches anything
     * (there is no way to tell what it measures).
     */
    internal fun unitsMatch(a: String?, b: String?): Boolean {
        val na = a?.trim()?.lowercase().orEmpty()
        val nb = b?.trim()?.lowercase().orEmpty()
        return na.isNotEmpty() && na == nb
    }
}

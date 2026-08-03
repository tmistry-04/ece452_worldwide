package com.example.pantryparty.recipe

import com.example.pantryparty.pantry.StockItem
import com.example.pantryparty.network.RecipeInformation
import kotlin.math.roundToInt

/**
 * One editable deduction in the "I made this" dialog: a pantry row, a suggested
 * starting amount, and the pantry row's unit. The user adjusts [suggested] in the
 * dialog (in whole units) before anything is written to the pantry.
 */
data class ConsumeLine(
    val item: StockItem,
    val suggested: Int,   // prefill in pantry units, clamped to what's on hand
    val unit: String
)

/**
 * The editable deduction plan for a recipe.
 *
 * @param lines   pantry rows that can be deducted, each with a suggested amount.
 * @param skipped ingredient names the recipe needs but that aren't in the pantry,
 *                so there's nothing to deduct.
 */
data class ConsumePlan(
    val lines: List<ConsumeLine>,
    val skipped: List<String>
)

/**
 * Pure, Android-free deduction logic (testable like [RecipeMatcher]).
 *
 * Builds a *suggested* plan only — it never writes to the pantry. The user adjusts
 * the amounts in the dialog and the confirmed values are applied by the caller.
 *
 * Policy (confirmed with the user): because the deduction is now manual, every
 * recipe ingredient that's in the pantry gets an editable line regardless of unit
 * — the user picks how many they used. We prefill from the recipe amount only when
 * the units match (otherwise we can't convert, so it starts at 0). Ingredients not
 * in the pantry are reported as `skipped`; staples (see [nonStapleIds]) are assumed
 * on hand and left out entirely (the card lists them separately).
 */
object PantryConsumer {

    /**
     * @param nonStapleIds the recipe's non-staple ingredient ids (the search's
     * used∪missed set). Ingredients whose id is absent from this set are pantry
     * staples — they are omitted from the plan entirely. Pass null to include
     * every ingredient.
     */
    fun plan(
        pantry: List<StockItem>,
        recipe: RecipeInformation,
        nonStapleIds: Set<Int>? = null
    ): ConsumePlan {
        // Index the pantry by Spoonacular id for O(1) lookups per ingredient.
        val byId = RecipeMatcher.indexByIngredient(pantry)

        val lines = mutableListOf<ConsumeLine>()
        val skipped = mutableListOf<String>()

        for (required in recipe.extendedIngredients) {
            // Staple (assumed on hand): not part of the manual deduction.
            if (nonStapleIds != null && required.id !in nonStapleIds) continue

            val have = byId[required.id]
            if (have == null) {
                // Not in the pantry -> nothing to deduct.
                skipped += required.name
            } else {
                // In the pantry: editable line. Prefill from the recipe amount only
                // when units match; otherwise start at 0 and let the user choose.
                val suggested = if (RecipeMatcher.unitsMatch(have.unit, required.unit)) {
                    required.amount.roundToInt().coerceIn(0, have.quantity)
                } else {
                    0
                }
                lines += ConsumeLine(have, suggested, have.unit)
            }
        }

        return ConsumePlan(lines, skipped)
    }
}

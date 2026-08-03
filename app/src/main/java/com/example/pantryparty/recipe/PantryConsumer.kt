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
 * — the user picks how many they used. We prefill from the recipe amount when the
 * units match, or from the recipe amount converted into pantry units when they
 * differ but are convertible (see [RecipeMatcher.convert]); when there's no
 * conversion — an unrecognized unit, or weight vs volume — the line starts at 0.
 * Ingredients not in the pantry are reported as `skipped`; staples (see
 * [nonStapleIds]) are assumed on hand and left out entirely (the card lists them
 * separately).
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
        nonStapleIds: Set<Int>? = null,
        alwaysHave: StapleSet = StapleSet.EMPTY
    ): ConsumePlan {
        // Index the pantry once for O(1) lookups per ingredient.
        val index = RecipeMatcher.indexPantry(pantry)

        val lines = mutableListOf<ConsumeLine>()
        val skipped = mutableListOf<String>()
        // Catalog ids already given a line, so one pantry row can't be deducted twice.
        val claimed = mutableSetOf<Long>()

        for (required in recipe.extendedIngredients) {
            // Staple (assumed on hand): not part of the manual deduction.
            if (RecipeMatcher.isStaple(required, index, nonStapleIds, alwaysHave)) continue

            val have = index.find(required)
            if (have == null) {
                // Not in the pantry -> nothing to deduct.
                skipped += required.name
                continue
            }

            // Another ingredient already claimed this row — e.g. a recipe listing both
            // "butter" (1001) and "unsalted butter" (1145), which resolve to the same
            // pantry row by name. One line per row, so confirming can't double-deduct.
            if (!claimed.add(have.id)) continue

            // In the pantry: editable line. Prefill with the recipe amount in
            // pantry units — directly when the units match, converted when they
            // differ but are convertible. Null means neither, so the user starts
            // at 0 and chooses for themselves.
            val inPantryUnits: Double? =
                if (RecipeMatcher.unitsMatch(have.unit, required.unit)) required.amount
                else RecipeMatcher.convert(required.amount, required.unit, have.unit)

            val suggested = inPantryUnits?.roundToInt()?.coerceIn(0, have.quantity) ?: 0
            lines += ConsumeLine(have, suggested, have.unit)
        }

        return ConsumePlan(lines, skipped)
    }
}

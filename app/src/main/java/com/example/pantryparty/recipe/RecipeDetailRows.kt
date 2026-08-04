package com.example.pantryparty.recipe

import com.example.pantryparty.network.ExtendedIngredient
import com.example.pantryparty.network.RecipeInformation
import java.util.IdentityHashMap

/** How one recipe ingredient stands against the pantry. */
enum class IngredientStatus {
    /** On hand in a sufficient amount (or in an amount that can't be compared). */
    HAVE,

    /** Tracked, but less than the recipe asks for. */
    SHORT,

    /** Not in the pantry at all. */
    MISSING,

    /** Salt, water, oil… assumed on hand and never amount-checked. */
    STAPLE
}

data class DetailIngredientRow(
    val required: ExtendedIngredient,
    val status: IngredientStatus,
    /** Only set for [IngredientStatus.SHORT]: how much is actually on hand. */
    val haveQuantity: Int? = null,
    val haveUnit: String? = null
)

object RecipeDetailRows {

    /**
     * Flattens a [RecipeMatch] plus its staples back into the recipe's *own*
     * ingredient order. [RecipeMatcher.match] returns three separate buckets,
     * which is right for the card's "you have / you need" summary but wrong for a
     * recipe page — a recipe lists its ingredients the way you'd shop for them,
     * not grouped by what you happen to own.
     *
     * [match] must have been produced from [info]: the lookup is keyed by object
     * identity, since `available`, `missing`, and `staples` all hold the very
     * instances found in `info.extendedIngredients`.
     */
    fun build(
        info: RecipeInformation,
        match: RecipeMatch,
        staples: List<ExtendedIngredient>
    ): List<DetailIngredientRow> {
        // Identity, not equality: ExtendedIngredient is a data class, and recipes
        // really do repeat a line verbatim (salt once per section). A value-keyed
        // map would silently merge those into one row.
        val byIngredient = IdentityHashMap<ExtendedIngredient, DetailIngredientRow>()

        staples.forEach {
            byIngredient[it] = DetailIngredientRow(it, IngredientStatus.STAPLE)
        }
        match.available.forEach {
            byIngredient[it.required] = DetailIngredientRow(it.required, IngredientStatus.HAVE)
        }
        match.missing.forEach {
            // haveQuantity distinguishes "you have some, just not enough" from
            // "you don't have this" — the same split AmountDetail makes on the card.
            byIngredient[it.required] = DetailIngredientRow(
                required = it.required,
                status = if (it.haveQuantity != null) IngredientStatus.SHORT else IngredientStatus.MISSING,
                haveQuantity = it.haveQuantity,
                haveUnit = it.haveUnit
            )
        }

        return info.extendedIngredients.map { required ->
            // Unreachable in practice: match() visits every extendedIngredient and
            // either skips it as a staple or files it under exactly one bucket.
            byIngredient[required] ?: DetailIngredientRow(required, IngredientStatus.STAPLE)
        }
    }

    /** "3 of 5 in your pantry" — staples are excluded from both halves. */
    fun haveCount(rows: List<DetailIngredientRow>): Int =
        rows.count { it.status == IngredientStatus.HAVE }

    fun checkedCount(rows: List<DetailIngredientRow>): Int =
        rows.count { it.status != IngredientStatus.STAPLE }
}

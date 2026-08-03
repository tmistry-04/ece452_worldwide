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

/** What a unit measures. Only units within the same dimension are comparable. */
internal enum class UnitDimension { WEIGHT, VOLUME }

/** An amount expressed in its dimension's base unit (grams for weight, ml for volume). */
internal data class BaseAmount(val amount: Double, val dimension: UnitDimension)

private val WHITESPACE = Regex("\\s+")

/** Canonical form of an ingredient name — [PantryIndex]'s fallback lookup key. */
internal fun normalizeIngredientName(name: String?): String =
    name?.trim()?.lowercase()?.replace(WHITESPACE, " ").orEmpty()

/**
 * The pantry, keyed for ingredient lookup and shared by [RecipeMatcher] and
 * [PantryConsumer] so both classify an ingredient identically.
 *
 * Spoonacular does not use one id per ingredient across endpoints. A pantry row's
 * id comes from `food/ingredients/autocomplete`, which answers "butter" with 1001,
 * while a recipe's `extendedIngredients` may carry 1145 ("unsalted butter") for the
 * same thing — so id-only matching reports stock you actually have as missing.
 *
 * [find] therefore falls back to the canonical ingredient `name`, which both
 * endpoints report identically ("butter" for 1001 and 1145 alike). The fallback is
 * exact equality on the normalized name, never a substring, so "peanut butter"
 * stays distinct from "butter".
 */
internal class PantryIndex(
    private val byId: Map<Int, StockItem>,
    private val byName: Map<String, StockItem>
) {
    fun find(required: ExtendedIngredient): StockItem? =
        byId[required.id] ?: byName[normalizeIngredientName(required.name)]
}

/**
 * The user's "always have" list — ingredients they've declared are always on hand.
 *
 * Keyed the same way as [PantryIndex], and for the same reason: the id stored when
 * the user picked "salt" from autocomplete need not be the id a recipe uses for it,
 * so an id-only set would silently ignore the user's choice.
 */
class StapleSet(
    private val byId: Set<Int>,
    private val byName: Set<String>
) {
    fun contains(required: ExtendedIngredient): Boolean {
        if (required.id in byId) return true
        val name = normalizeIngredientName(required.name)
        return name.isNotEmpty() && name in byName
    }

    companion object {
        val EMPTY = StapleSet(emptySet(), emptySet())

        /** Builds a set from stored (spoonacularId, name) pairs. */
        fun of(entries: List<Pair<Int, String>>): StapleSet = StapleSet(
            byId = entries.mapTo(mutableSetOf()) { it.first },
            byName = entries.mapNotNullTo(mutableSetOf()) {
                normalizeIngredientName(it.second).ifEmpty { null }
            }
        )
    }
}

/**
 * Pure pantry-vs-recipe comparison. No Android dependencies so it is unit-testable.
 *
 * Sufficiency rule: match by Spoonacular ingredient id, falling back to the
 * canonical ingredient name (see [PantryIndex]). When the pantry unit and the
 * recipe unit agree, compare amounts directly; when they differ but both are known
 * cooking units of the same dimension (see [toBaseUnits]), convert and compare. Only
 * when the amounts genuinely can't be compared — an unrecognized unit, or weight vs
 * volume — do we fall back to presence (treat the ingredient as available).
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
        nonStapleIds: Set<Int>? = null,
        alwaysHave: StapleSet = StapleSet.EMPTY
    ): RecipeMatch {
        // Index the pantry once for O(1) lookups per ingredient.
        val index = indexPantry(pantry)

        val available = mutableListOf<MatchedIngredient>()
        val missing = mutableListOf<MissingIngredient>()

        for (required in recipe.extendedIngredients) {
            // Staple (assumed on hand): not part of the amount check at all.
            if (isStaple(required, index, nonStapleIds, alwaysHave)) continue

            val have = index.find(required)
            if (have == null) {
                // Not in the pantry at all -> missing.
                missing += MissingIngredient(required, haveQuantity = null, haveUnit = null)
                continue
            }

            // Can we compare the amounts at all? Same unit, or two units we can
            // convert between. `null` means the comparison isn't possible.
            val enough: Boolean? =
                if (unitsMatch(have.unit, required.unit)) have.quantity >= required.amount
                else hasEnoughConverted(have, required)

            when (enough) {
                false -> missing += MissingIngredient(required, have.quantity, have.unit)
                // true  -> comparable and sufficient.
                // null  -> units aren't comparable, so fall back to presence.
                else -> available += MatchedIngredient(required, have)
            }
        }

        return RecipeMatch(recipe, available, missing)
    }

    /**
     * The recipe's staple ingredients — exactly the ones [match] and [PantryConsumer]
     * skip. Surfaced as their own list on the recipe card, so it must be computed with
     * the same [isStaple] rule or the card would advertise staples that actually get
     * amount-checked (or hide ones that don't).
     */
    fun staplesOf(
        pantry: List<StockItem>,
        recipe: RecipeInformation,
        nonStapleIds: Set<Int>?,
        alwaysHave: StapleSet = StapleSet.EMPTY
    ): List<ExtendedIngredient> {
        val index = indexPantry(pantry)
        return recipe.extendedIngredients.filter { isStaple(it, index, nonStapleIds, alwaysHave) }
    }

    /**
     * Whether [required] is assumed on hand, and so skipped entirely: no amount check,
     * no deduction line. Shared by [match], [staplesOf] and [PantryConsumer.plan] so
     * the three can never disagree about what counts as a staple.
     *
     * 1. The user's "always have" list wins outright — it's an explicit statement.
     * 2. Otherwise Spoonacular's guess (anything outside [nonStapleIds]) applies, but
     *    only to ingredients the pantry isn't tracking. Tracking something *is* the
     *    statement that you want it counted and deducted, so it is never a staple.
     *
     * Both lookups match on id or canonical name, so a pantry row or staple entry still
     * applies when the recipe carries a different id for the same ingredient.
     */
    internal fun isStaple(
        required: ExtendedIngredient,
        index: PantryIndex,
        nonStapleIds: Set<Int>?,
        alwaysHave: StapleSet
    ): Boolean {
        if (alwaysHave.contains(required)) return true
        // No search result to read staples off -> check every ingredient.
        if (nonStapleIds == null) return false
        if (index.find(required) != null) return false
        return required.id !in nonStapleIds
    }

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
     * Builds the id + name lookup shared with [PantryConsumer]. The name index is
     * derived from the merged id index so both views agree on quantities; a name
     * collision keeps the first row, matching [indexByIngredient]'s posture, and
     * blank names are excluded so they can't act as a match-all key.
     */
    internal fun indexPantry(pantry: List<StockItem>): PantryIndex {
        val byId = indexByIngredient(pantry)
        val byName = LinkedHashMap<String, StockItem>()
        for (row in byId.values) {
            val key = normalizeIngredientName(row.name)
            if (key.isNotEmpty() && key !in byName) byName[key] = row
        }
        return PantryIndex(byId, byName)
    }

    /**
     * Indexes the pantry by Spoonacular id. The DB enforces one row per ingredient,
     * so this is normally a 1:1 map — but it is defensive against duplicate rows:
     * same-unit duplicates have their quantities summed; if the units disagree there
     * is no safe way to combine, so the first row wins.
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

    // --- unit conversion -------------------------------------------------------

    /**
     * How many base units one of each known cooking unit is worth: grams for
     * [UnitDimension.WEIGHT], millilitres for [UnitDimension.VOLUME]. Keyed by the
     * singular form produced by [normalizeUnit]. US customary spoons and cups, so
     * the volume row stays self-consistent (1 cup = 16 tbsp = 48 tsp).
     */
    private val BASE_UNITS: Map<String, BaseAmount> = mapOf(
        "g" to BaseAmount(1.0, UnitDimension.WEIGHT),
        "gram" to BaseAmount(1.0, UnitDimension.WEIGHT),
        "kg" to BaseAmount(1000.0, UnitDimension.WEIGHT),
        "kilogram" to BaseAmount(1000.0, UnitDimension.WEIGHT),
        "oz" to BaseAmount(28.349523125, UnitDimension.WEIGHT),
        "ounce" to BaseAmount(28.349523125, UnitDimension.WEIGHT),
        "lb" to BaseAmount(453.59237, UnitDimension.WEIGHT),
        "pound" to BaseAmount(453.59237, UnitDimension.WEIGHT),

        "ml" to BaseAmount(1.0, UnitDimension.VOLUME),
        "milliliter" to BaseAmount(1.0, UnitDimension.VOLUME),
        "millilitre" to BaseAmount(1.0, UnitDimension.VOLUME),
        "l" to BaseAmount(1000.0, UnitDimension.VOLUME),
        "liter" to BaseAmount(1000.0, UnitDimension.VOLUME),
        "litre" to BaseAmount(1000.0, UnitDimension.VOLUME),
        "tsp" to BaseAmount(4.92892159375, UnitDimension.VOLUME),
        "teaspoon" to BaseAmount(4.92892159375, UnitDimension.VOLUME),
        "tbsp" to BaseAmount(14.78676478125, UnitDimension.VOLUME),
        "tablespoon" to BaseAmount(14.78676478125, UnitDimension.VOLUME),
        "cup" to BaseAmount(236.5882365, UnitDimension.VOLUME)
    )

    /** Lowercases, trims, and drops a trailing plural "s" ("Cups" -> "cup", "lbs" -> "lb"). */
    private fun normalizeUnit(unit: String?): String {
        val u = unit?.trim()?.lowercase().orEmpty()
        return if (u.length > 1 && u.endsWith("s")) u.dropLast(1) else u
    }

    /**
     * Converts [amount] of [unit] to its dimension's base unit (grams or millilitres),
     * reporting which dimension that is. Returns null for anything outside the two
     * supported groups — "piece", "clove", a blank unit — which callers read as
     * "these amounts can't be compared".
     */
    internal fun toBaseUnits(amount: Double, unit: String?): BaseAmount? {
        val base = BASE_UNITS[normalizeUnit(unit)] ?: return null
        return BaseAmount(amount * base.amount, base.dimension)
    }

    /**
     * Converts [amount] from one unit to another, or null when the two units aren't
     * both recognized members of the same dimension.
     */
    internal fun convert(amount: Double, from: String?, to: String?): Double? {
        val source = toBaseUnits(amount, from) ?: return null
        val target = toBaseUnits(1.0, to) ?: return null
        if (source.dimension != target.dimension) return null
        return source.amount / target.amount
    }

    /**
     * Sufficiency check across differing units. Null when the amounts aren't
     * comparable, which is [match]'s signal to fall back to presence.
     */
    private fun hasEnoughConverted(have: StockItem, required: ExtendedIngredient): Boolean? {
        val stocked = toBaseUnits(have.quantity.toDouble(), have.unit) ?: return null
        val needed = toBaseUnits(required.amount, required.unit) ?: return null
        if (stocked.dimension != needed.dimension) return null
        return stocked.amount >= needed.amount
    }
}

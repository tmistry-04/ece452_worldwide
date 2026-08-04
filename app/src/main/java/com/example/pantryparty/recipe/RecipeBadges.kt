package com.example.pantryparty.recipe

import com.example.pantryparty.network.RecipeInformation

/**
 * The chips along the top of the recipe detail page: which diets a recipe
 * satisfies, and what kind of dish it is.
 *
 * Diet information arrives two ways — as booleans (`glutenFree`) and as free
 * strings (`diets: ["gluten free"]`) — which overlap. Both are folded into one
 * deduplicated list of labels worded exactly like [RecipeFilters.DIETS], so a
 * badge here reads the same as the filter the user checked on the search panel.
 */
object RecipeBadges {

    fun diets(info: RecipeInformation): List<String> {
        val labels = LinkedHashSet<String>()
        // Booleans first, in a fixed order, so the row is stable across recipes.
        if (info.glutenFree) labels += "Gluten Free"
        if (info.dairyFree) labels += "Dairy Free"
        if (info.vegetarian) labels += "Vegetarian"
        if (info.vegan) labels += "Vegan"
        if (info.ketogenic) labels += "Ketogenic"
        if (info.lowFodmap) labels += "Low FODMAP"
        if (info.whole30) labels += "Whole30"
        info.diets.forEach { raw -> canonicalDiet(raw)?.let { labels += it } }
        return labels.toList()
    }

    /** Dish types and cuisines merged — "Dessert", "Snack", "American". */
    fun categories(info: RecipeInformation): List<String> {
        val labels = LinkedHashSet<String>()
        (info.dishTypes + info.cuisines).forEach { raw ->
            titleCaseWords(raw)?.let { labels += it }
        }
        return labels.toList()
    }

    /**
     * The API's diet strings do not match the filter panel's labels — it says
     * "lacto ovo vegetarian" where the panel says "Vegetarian", and "fodmap
     * friendly" where the panel says "Low FODMAP". Unrecognized values are kept
     * (title-cased) rather than dropped, so a new Spoonacular diet still shows up.
     */
    internal fun canonicalDiet(raw: String): String? {
        val key = normalize(raw) ?: return null
        return DIET_ALIASES[key] ?: titleCaseWords(raw)
    }

    private val DIET_ALIASES = mapOf(
        "gluten free" to "Gluten Free",
        "dairy free" to "Dairy Free",
        "ketogenic" to "Ketogenic",
        "keto" to "Ketogenic",
        "lacto ovo vegetarian" to "Vegetarian",
        "vegetarian" to "Vegetarian",
        "lacto vegetarian" to "Lacto-Vegetarian",
        "ovo vegetarian" to "Ovo-Vegetarian",
        "vegan" to "Vegan",
        "pescatarian" to "Pescetarian",
        "pescetarian" to "Pescetarian",
        "paleolithic" to "Paleo",
        "paleo" to "Paleo",
        "primal" to "Primal",
        "fodmap friendly" to "Low FODMAP",
        "low fodmap" to "Low FODMAP",
        "whole 30" to "Whole30",
        "whole30" to "Whole30"
    )

    /** Lowercased, punctuation folded to single spaces: "Lacto-Ovo Vegetarian" -> "lacto ovo vegetarian". */
    private fun normalize(raw: String): String? =
        raw.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .takeIf { it.isNotEmpty() }

    /** "main course" -> "Main Course"; leaves existing capitals alone ("FODMAP"). */
    private fun titleCaseWords(raw: String): String? =
        raw.trim()
            .takeIf { it.isNotEmpty() }
            ?.split(" ")
            ?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}

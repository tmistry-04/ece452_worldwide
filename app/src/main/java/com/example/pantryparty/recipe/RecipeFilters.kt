package com.example.pantryparty.recipe

/**
 * One min/max bound pair for a nutrient filter. Values are kept as the raw text
 * the user typed so text fields can bind directly; [RecipeFilters.toQueryMap]
 * validates and skips anything that isn't a non-negative number.
 */
data class NutrientRange(val min: String = "", val max: String = "") {
    val isActive: Boolean get() = min.isNotBlank() || max.isNotBlank()
}

/** One nutrient the API can bound, e.g. param "VitaminC" -> minVitaminC/maxVitaminC. */
data class NutrientSpec(
    val label: String,
    val param: String,
    val unit: String,
    val group: String
)

/** One entry of the sort dropdown; [param] is the API value ("" = API default order). */
data class SortOption(val param: String, val label: String)

/**
 * User-editable search filters, mapped 1:1 onto `recipes/complexSearch` query
 * parameters by [toQueryMap]. Free-text numeric fields stay raw strings (see
 * [NutrientRange]); everything else is already valid by construction.
 *
 * Not represented: account-bound params (author, recipeBoxId, tags) which need a
 * connected Spoonacular user, and plumbing params (offset, number, fillIngredients,
 * ignorePantry, addRecipeInformation) which the app controls itself.
 */
data class RecipeFilters(
    val query: String = "",
    val titleMatch: String = "",
    val cuisines: Set<String> = emptySet(),
    val excludeCuisines: Set<String> = emptySet(),
    val diets: Set<String> = emptySet(),
    val intolerances: Set<String> = emptySet(),
    val mealType: String = "",
    val equipment: String = "",
    val excludeIngredients: String = "",
    val maxReadyTime: String = "",
    val minServings: String = "",
    val maxServings: String = "",
    val instructionsRequired: Boolean = true,
    val sort: String = "",
    val sortDirection: String = "desc",
    val nutrients: Map<String, NutrientRange> = emptyMap()
) {

    /** How many individual criteria are set — drives the "Filters (n)" badges. */
    val activeFilterCount: Int
        get() = (if (query.isNotBlank()) 1 else 0) +
            (if (titleMatch.isNotBlank()) 1 else 0) +
            cuisines.size + excludeCuisines.size + diets.size + intolerances.size +
            (if (mealType.isNotEmpty()) 1 else 0) +
            (if (equipment.isNotBlank()) 1 else 0) +
            (if (excludeIngredients.isNotBlank()) 1 else 0) +
            (if (validNumber(maxReadyTime) != null) 1 else 0) +
            (if (validNumber(minServings) != null) 1 else 0) +
            (if (validNumber(maxServings) != null) 1 else 0) +
            (if (sort.isNotEmpty()) 1 else 0) +
            (if (!instructionsRequired) 1 else 0) +   // non-default
            nutrients.values.count { it.isActive }

    /**
     * The complexSearch query parameters these filters stand for. Multi-selects
     * are comma-joined (which the API reads as AND for diets, OR-ish for the
     * rest); numeric fields are dropped unless they parse as a non-negative
     * number, so half-typed input can never produce a broken request.
     */
    fun toQueryMap(): Map<String, String> = buildMap {
        query.trim().takeIf { it.isNotEmpty() }?.let { put("query", it) }
        titleMatch.trim().takeIf { it.isNotEmpty() }?.let { put("titleMatch", it) }
        putJoined("cuisine", cuisines)
        putJoined("excludeCuisine", excludeCuisines)
        putJoined("diet", diets)
        putJoined("intolerances", intolerances)
        mealType.takeIf { it.isNotEmpty() }?.let { put("type", it) }
        equipment.trim().takeIf { it.isNotEmpty() }?.let { put("equipment", it) }
        excludeIngredients.trim().takeIf { it.isNotEmpty() }?.let { put("excludeIngredients", it) }
        validNumber(maxReadyTime)?.let { put("maxReadyTime", it) }
        validNumber(minServings)?.let { put("minServings", it) }
        validNumber(maxServings)?.let { put("maxServings", it) }
        put("instructionsRequired", instructionsRequired.toString())
        if (sort.isNotEmpty()) {
            put("sort", sort)
            put("sortDirection", sortDirection)
        }
        for (spec in NUTRIENTS) {
            val range = nutrients[spec.param] ?: continue
            validNumber(range.min)?.let { put("min${spec.param}", it) }
            validNumber(range.max)?.let { put("max${spec.param}", it) }
        }
    }

    /** Rebuilds one nutrient's range; a range cleared back to blank leaves the map. */
    fun withNutrient(param: String, transform: (NutrientRange) -> NutrientRange): RecipeFilters {
        val next = transform(nutrients[param] ?: NutrientRange())
        return copy(nutrients = if (next.isActive) nutrients + (param to next) else nutrients - param)
    }

    private fun MutableMap<String, String>.putJoined(key: String, values: Set<String>) {
        if (values.isNotEmpty()) put(key, values.joinToString(","))
    }

    companion object {
        private fun validNumber(raw: String): String? {
            val trimmed = raw.trim()
            val value = trimmed.toDoubleOrNull() ?: return null
            return if (value >= 0) trimmed else null
        }

        fun sortLabel(param: String): String =
            SORTS.firstOrNull { it.param == param }?.label ?: param

        val CUISINES = listOf(
            "African", "American", "Asian", "British", "Cajun", "Caribbean", "Chinese",
            "Eastern European", "European", "French", "German", "Greek", "Indian", "Irish",
            "Italian", "Japanese", "Jewish", "Korean", "Latin American", "Mediterranean",
            "Mexican", "Middle Eastern", "Nordic", "Southern", "Spanish", "Thai", "Vietnamese"
        )

        val DIETS = listOf(
            "Gluten Free", "Ketogenic", "Vegetarian", "Lacto-Vegetarian", "Ovo-Vegetarian",
            "Vegan", "Pescetarian", "Paleo", "Primal", "Low FODMAP", "Whole30"
        )

        val INTOLERANCES = listOf(
            "Dairy", "Egg", "Gluten", "Grain", "Peanut", "Seafood", "Sesame",
            "Shellfish", "Soy", "Sulfite", "Tree Nut", "Wheat"
        )

        val MEAL_TYPES = listOf(
            "main course", "side dish", "dessert", "appetizer", "salad", "bread",
            "breakfast", "soup", "beverage", "sauce", "marinade", "fingerfood",
            "snack", "drink"
        )

        val SORTS = listOf(
            SortOption("", "Best match (default)"),
            SortOption("popularity", "Popularity"),
            SortOption("healthiness", "Healthiness"),
            SortOption("time", "Preparation time"),
            SortOption("price", "Price"),
            SortOption("random", "Random"),
            SortOption("max-used-ingredients", "Most pantry ingredients used"),
            SortOption("min-missing-ingredients", "Fewest missing ingredients"),
            SortOption("meta-score", "Meta score"),
            SortOption("calories", "Calories"),
            SortOption("carbs", "Carbs"),
            SortOption("protein", "Protein"),
            SortOption("total-fat", "Fat"),
            SortOption("saturated-fat", "Saturated fat"),
            SortOption("fiber", "Fiber"),
            SortOption("sugar", "Sugar"),
            SortOption("sodium", "Sodium"),
            SortOption("cholesterol", "Cholesterol"),
            SortOption("alcohol", "Alcohol"),
            SortOption("caffeine", "Caffeine"),
            SortOption("energy", "Energy"),
            SortOption("calcium", "Calcium"),
            SortOption("choline", "Choline"),
            SortOption("copper", "Copper"),
            SortOption("fluoride", "Fluoride"),
            SortOption("folate", "Folate"),
            SortOption("folic-acid", "Folic acid"),
            SortOption("iodine", "Iodine"),
            SortOption("iron", "Iron"),
            SortOption("magnesium", "Magnesium"),
            SortOption("manganese", "Manganese"),
            SortOption("phosphorus", "Phosphorus"),
            SortOption("potassium", "Potassium"),
            SortOption("selenium", "Selenium"),
            SortOption("zinc", "Zinc"),
            SortOption("vitamin-a", "Vitamin A"),
            SortOption("vitamin-b1", "Vitamin B1"),
            SortOption("vitamin-b2", "Vitamin B2"),
            SortOption("vitamin-b3", "Vitamin B3"),
            SortOption("vitamin-b5", "Vitamin B5"),
            SortOption("vitamin-b6", "Vitamin B6"),
            SortOption("vitamin-b12", "Vitamin B12"),
            SortOption("vitamin-c", "Vitamin C"),
            SortOption("vitamin-d", "Vitamin D"),
            SortOption("vitamin-e", "Vitamin E"),
            SortOption("vitamin-k", "Vitamin K")
        )

        private const val MACROS = "Macros & energy"
        private const val MICROS = "Vitamins & minerals"

        /** Every nutrient complexSearch can bound, in display order. */
        val NUTRIENTS = listOf(
            NutrientSpec("Calories", "Calories", "kcal", MACROS),
            NutrientSpec("Carbs", "Carbs", "g", MACROS),
            NutrientSpec("Protein", "Protein", "g", MACROS),
            NutrientSpec("Fat", "Fat", "g", MACROS),
            NutrientSpec("Saturated fat", "SaturatedFat", "g", MACROS),
            NutrientSpec("Fiber", "Fiber", "g", MACROS),
            NutrientSpec("Sugar", "Sugar", "g", MACROS),
            NutrientSpec("Cholesterol", "Cholesterol", "mg", MACROS),
            NutrientSpec("Sodium", "Sodium", "mg", MACROS),
            NutrientSpec("Alcohol", "Alcohol", "g", MACROS),
            NutrientSpec("Caffeine", "Caffeine", "mg", MACROS),
            NutrientSpec("Vitamin A", "VitaminA", "IU", MICROS),
            NutrientSpec("Vitamin B1 (thiamin)", "VitaminB1", "mg", MICROS),
            NutrientSpec("Vitamin B2 (riboflavin)", "VitaminB2", "mg", MICROS),
            NutrientSpec("Vitamin B3 (niacin)", "VitaminB3", "mg", MICROS),
            NutrientSpec("Vitamin B5", "VitaminB5", "mg", MICROS),
            NutrientSpec("Vitamin B6", "VitaminB6", "mg", MICROS),
            NutrientSpec("Vitamin B12", "VitaminB12", "µg", MICROS),
            NutrientSpec("Vitamin C", "VitaminC", "mg", MICROS),
            NutrientSpec("Vitamin D", "VitaminD", "µg", MICROS),
            NutrientSpec("Vitamin E", "VitaminE", "mg", MICROS),
            NutrientSpec("Vitamin K", "VitaminK", "µg", MICROS),
            NutrientSpec("Folate", "Folate", "µg", MICROS),
            NutrientSpec("Folic acid", "FolicAcid", "µg", MICROS),
            NutrientSpec("Calcium", "Calcium", "mg", MICROS),
            NutrientSpec("Choline", "Choline", "mg", MICROS),
            NutrientSpec("Copper", "Copper", "mg", MICROS),
            NutrientSpec("Fluoride", "Fluoride", "mg", MICROS),
            NutrientSpec("Iodine", "Iodine", "µg", MICROS),
            NutrientSpec("Iron", "Iron", "mg", MICROS),
            NutrientSpec("Magnesium", "Magnesium", "mg", MICROS),
            NutrientSpec("Manganese", "Manganese", "mg", MICROS),
            NutrientSpec("Phosphorus", "Phosphorus", "mg", MICROS),
            NutrientSpec("Potassium", "Potassium", "mg", MICROS),
            NutrientSpec("Selenium", "Selenium", "µg", MICROS),
            NutrientSpec("Zinc", "Zinc", "mg", MICROS)
        )
    }
}

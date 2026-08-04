package com.example.pantryparty.recipe

import com.example.pantryparty.network.RecipeInformation
import kotlin.math.roundToInt

/** One headline number, preformatted so the composable only has to position text. */
data class NutritionFact(val label: String, val value: String)

object RecipeNutrition {

    // Exactly the strings the API uses, matched by EQUALITY. Never startsWith or
    // contains: the nutrients array also carries "Saturated Fat" and "Net
    // Carbohydrates", and a prefix match would cheerfully report one of those
    // instead — wrong by a factor of two with nothing on screen to show it.
    private val HEADLINE = listOf(
        "calories" to "Calories",
        "protein" to "Protein",
        "carbohydrates" to "Carbs",
        "fat" to "Fat"
    )

    /**
     * Calories / Protein / Carbs / Fat for a single serving, in that fixed order.
     * Spoonacular already reports `nutrition.nutrients` per serving, so there is no
     * dividing to do here.
     *
     * A nutrient the API didn't report is left out rather than shown as zero —
     * "0 g protein" is a claim, an absent row is not — so an empty list means the
     * strip shouldn't be drawn at all.
     */
    fun perServing(info: RecipeInformation): List<NutritionFact> {
        val byName = info.nutrition?.nutrients
            ?.associateBy { it.name.trim().lowercase() }
            ?: return emptyList()

        return HEADLINE.mapNotNull { (apiName, label) ->
            val nutrient = byName[apiName] ?: return@mapNotNull null
            val amount = nutrient.amount.roundToInt()
            val unit = nutrient.unit.trim()
            NutritionFact(label, if (unit.isEmpty()) "$amount" else "$amount $unit")
        }
    }
}

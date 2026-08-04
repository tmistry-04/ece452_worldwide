package com.example.pantryparty

import com.example.pantryparty.network.Nutrient
import com.example.pantryparty.network.Nutrition
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.recipe.RecipeNutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeNutritionTest {

    private fun info(vararg nutrients: Pair<String, Double>) = RecipeInformation(
        id = 1, title = "Test",
        nutrition = Nutrition(
            nutrients.map { (name, amount) ->
                Nutrient(
                    name = name,
                    amount = amount,
                    unit = if (name.trim().equals("Calories", ignoreCase = true)) "kcal" else "g"
                )
            }
        )
    )

    @Test
    fun reportsTheFourHeadlineNumbersInAFixedOrder() {
        // Deliberately supplied out of order — the display order must not depend on
        // however the API happened to sort them.
        val facts = RecipeNutrition.perServing(
            info("Protein" to 27.0, "Calories" to 324.76, "Fat" to 11.93, "Carbohydrates" to 26.41)
        )
        assertEquals(listOf("Calories", "Protein", "Carbs", "Fat"), facts.map { it.label })
        assertEquals(listOf("325 kcal", "27 g", "26 g", "12 g"), facts.map { it.value })
    }

    @Test
    fun matchesFatExactly_notSaturatedFat() {
        // The single most likely way to ship this quietly wrong: a contains/startsWith
        // match would report saturated fat as total fat.
        val facts = RecipeNutrition.perServing(info("Saturated Fat" to 5.38, "Fat" to 11.93))
        assertEquals(listOf("Fat"), facts.map { it.label })
        assertEquals("12 g", facts.single().value)
    }

    @Test
    fun matchesCarbohydratesExactly_notNetCarbohydrates() {
        val facts = RecipeNutrition.perServing(info("Net Carbohydrates" to 21.36, "Carbohydrates" to 26.41))
        assertEquals(listOf("Carbs"), facts.map { it.label })
        assertEquals("26 g", facts.single().value)
    }

    @Test
    fun omitsNutrientsTheApiDidNotReport() {
        // "0 g protein" would be a claim; leaving it out is not.
        val facts = RecipeNutrition.perServing(info("Calories" to 100.0))
        assertEquals(listOf("Calories"), facts.map { it.label })
    }

    @Test
    fun noNutritionYieldsAnEmptyList() {
        assertTrue(RecipeNutrition.perServing(RecipeInformation(id = 1, title = "Test")).isEmpty())
        assertTrue(RecipeNutrition.perServing(info()).isEmpty())
    }

    @Test
    fun isCaseAndWhitespaceInsensitiveOnNames() {
        val facts = RecipeNutrition.perServing(info(" CALORIES " to 200.0))
        assertEquals("200 kcal", facts.single().value)
    }
}

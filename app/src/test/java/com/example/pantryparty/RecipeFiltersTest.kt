package com.example.pantryparty

import com.example.pantryparty.recipe.NutrientRange
import com.example.pantryparty.recipe.RecipeFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeFiltersTest {

    @Test
    fun emptyFilters_emitOnlyTheInstructionsDefault() {
        val params = RecipeFilters().toQueryMap()
        assertEquals(mapOf("instructionsRequired" to "true"), params)
        assertEquals(0, RecipeFilters().activeFilterCount)
    }

    @Test
    fun setFilters_mapToTheirQueryParams() {
        val params = RecipeFilters(
            query = " pasta ",
            titleMatch = "chicken",
            cuisines = setOf("Italian", "Greek"),
            excludeCuisines = setOf("Nordic"),
            diets = setOf("Vegan"),
            intolerances = setOf("Peanut", "Soy"),
            mealType = "main course",
            equipment = "blender",
            excludeIngredients = "olives",
            maxReadyTime = "45",
            minServings = "2",
            maxServings = "6",
            instructionsRequired = false,
            nutrients = mapOf(
                "VitaminC" to NutrientRange(min = "10", max = "200"),
                "Carbs" to NutrientRange(max = "80")
            )
        ).toQueryMap()

        assertEquals("pasta", params["query"])                   // trimmed
        assertEquals("chicken", params["titleMatch"])
        assertEquals(setOf("Italian", "Greek"), params["cuisine"]!!.split(",").toSet())
        assertEquals("Nordic", params["excludeCuisine"])
        assertEquals("Vegan", params["diet"])
        assertEquals(setOf("Peanut", "Soy"), params["intolerances"]!!.split(",").toSet())
        assertEquals("main course", params["type"])
        assertEquals("blender", params["equipment"])
        assertEquals("olives", params["excludeIngredients"])
        assertEquals("45", params["maxReadyTime"])
        assertEquals("2", params["minServings"])
        assertEquals("6", params["maxServings"])
        assertEquals("false", params["instructionsRequired"])
        assertEquals("10", params["minVitaminC"])
        assertEquals("200", params["maxVitaminC"])
        assertEquals("80", params["maxCarbs"])
        assertNull(params["minCarbs"])
    }

    @Test
    fun numericFields_dropAnythingThatIsNotANonNegativeNumber() {
        val params = RecipeFilters(
            maxReadyTime = "abc",
            minServings = "-2",
            nutrients = mapOf(
                "Iron" to NutrientRange(min = "not a number", max = "12.5")
            )
        ).toQueryMap()

        assertNull(params["maxReadyTime"])
        assertNull(params["minServings"])
        assertNull(params["minIron"])
        assertEquals("12.5", params["maxIron"])
    }

    @Test
    fun sortDirection_isOnlyEmittedAlongsideASort() {
        assertNull(RecipeFilters().toQueryMap()["sortDirection"])

        val params = RecipeFilters(sort = "vitamin-c", sortDirection = "asc").toQueryMap()
        assertEquals("vitamin-c", params["sort"])
        assertEquals("asc", params["sortDirection"])
    }

    @Test
    fun activeFilterCount_countsEachIndividualCriterion() {
        val filters = RecipeFilters(
            query = "soup",
            cuisines = setOf("Thai", "Indian"),
            intolerances = setOf("Gluten"),
            maxReadyTime = "30",
            instructionsRequired = false,   // non-default counts
            nutrients = mapOf(
                "Calories" to NutrientRange(max = "600"),
                "Protein" to NutrientRange()   // blank -> inactive
            )
        )
        assertEquals(1 + 2 + 1 + 1 + 1 + 1, filters.activeFilterCount)
    }

    @Test
    fun withNutrient_addsUpdatesAndRemovesBlankRanges() {
        val added = RecipeFilters().withNutrient("Zinc") { it.copy(min = "5") }
        assertEquals(NutrientRange(min = "5"), added.nutrients["Zinc"])

        val cleared = added.withNutrient("Zinc") { it.copy(min = "") }
        assertFalse("Zinc" in cleared.nutrients)   // blank ranges leave the map
        assertEquals(0, cleared.activeFilterCount)
    }

    @Test
    fun nutrientCatalog_coversTheDocumentedParams() {
        val params = RecipeFilters.NUTRIENTS.map { it.param }
        // Spot-check the families against the documented complexSearch list.
        assertTrue(params.containsAll(listOf("Calories", "Carbs", "Protein", "Fat", "SaturatedFat")))
        assertTrue(
            params.containsAll(
                listOf(
                    "VitaminA", "VitaminB1", "VitaminB2", "VitaminB3", "VitaminB5",
                    "VitaminB6", "VitaminB12", "VitaminC", "VitaminD", "VitaminE", "VitaminK"
                )
            )
        )
        assertTrue(params.containsAll(listOf("Folate", "FolicAcid", "Iodine", "Selenium", "Zinc")))
        assertEquals(params.size, params.toSet().size)   // no duplicate params
    }
}

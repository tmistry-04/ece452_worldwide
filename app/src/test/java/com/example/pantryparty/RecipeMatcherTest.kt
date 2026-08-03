package com.example.pantryparty

import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.ExtendedIngredient
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.RecipeIngredientBrief
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.recipe.RecipeMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeMatcherTest {

    // --- helpers --------------------------------------------------------------

    private fun pantry(id: Int, qty: Int, unit: String, name: String = "x") =
        PantryItem(name = name, quantity = qty, unit = unit, spoonacularId = id)

    private fun ing(id: Int, amount: Double, unit: String, name: String = "x") =
        ExtendedIngredient(id = id, name = name, amount = amount, unit = unit, original = null, measures = null)

    private fun recipe(vararg ingredients: ExtendedIngredient) =
        RecipeInformation(
            id = 1, title = "Test", image = null, readyInMinutes = 10, servings = 2,
            extendedIngredients = ingredients.toList()
        )

    // bucketByMissed counts off the `missedIngredients` list, so populate it.
    private fun byIngredient(id: Int, missed: Int) =
        RecipeByIngredient(
            id = id, title = "R$id", image = null,
            usedIngredientCount = 0, missedIngredientCount = missed,
            missedIngredients = List(missed) { i ->
                RecipeIngredientBrief(id = id * 100 + i, name = "m$i", amount = 1.0, unit = "g", original = null, image = null)
            }
        )

    // --- tests ----------------------------------------------------------------

    @Test
    fun allPresentAndSufficient_isMissingNone() {
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1, qty = 3, unit = "cup"), pantry(2, qty = 5, unit = "g")),
            recipe = recipe(ing(1, 2.0, "cup"), ing(2, 4.0, "g"))
        )
        assertEquals(0, match.missingCount)
        assertTrue(match.canMake)
        assertEquals(2, match.available.size)
    }

    @Test
    fun absentIngredient_isMissing() {
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1, qty = 3, unit = "cup")),
            recipe = recipe(ing(1, 2.0, "cup"), ing(99, 1.0, "g"))
        )
        assertEquals(1, match.missingCount)
        assertFalse(match.canMake)
        assertEquals(99, match.missing.single().required.id)
        // Absent -> we report no held quantity.
        assertEquals(null, match.missing.single().haveQuantity)
    }

    @Test
    fun insufficientAmountSameUnit_isMissing() {
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1, qty = 1, unit = "cup")),
            recipe = recipe(ing(1, 2.0, "cup"))
        )
        assertEquals(1, match.missingCount)
        assertEquals(1, match.missing.single().haveQuantity)
    }

    @Test
    fun differentUnits_fallsBackToPresentAndAvailable() {
        // Pantry in grams, recipe wants cups -> no conversion, treat as available.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1, qty = 1, unit = "grams")),
            recipe = recipe(ing(1, 2.0, "cup"))
        )
        assertEquals(0, match.missingCount)
        assertTrue(match.canMake)
    }

    @Test
    fun staples_areNotCountedAsMissing() {
        // salt (id 2) is absent from the pantry but it's a staple -> skipped by the
        // amount check, so it never shows as "short" and the recipe stays makeable.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1, qty = 3, unit = "cup")),
            recipe = recipe(ing(1, 2.0, "cup"), ing(2, 1.0, "tsp", name = "salt")),
            nonStapleIds = setOf(1)
        )
        assertEquals(0, match.missingCount)
        assertTrue(match.canMake)
    }

    @Test
    fun staplesOf_returnsIngredientsOutsideTheNonStapleSet() {
        // Only id 1 is a "real" ingredient; everything else the recipe needs is a staple.
        val staples = RecipeMatcher.staplesOf(
            recipe = recipe(ing(1, 2.0, "cup", name = "flour"), ing(2, 1.0, "tsp", name = "salt")),
            nonStapleIds = setOf(1)
        )
        assertEquals(listOf("salt"), staples.map { it.name })
    }

    @Test
    fun bucketByMissed_dropsOverThree_andSortsReadyFirst() {
        val input = listOf(
            byIngredient(id = 1, missed = 2),
            byIngredient(id = 2, missed = 4),   // dropped (> MAX_MISSING)
            byIngredient(id = 3, missed = 0),
            byIngredient(id = 4, missed = 3)
        )

        val out = RecipeMatcher.bucketByMissed(input)

        assertEquals(3, out.size)                          // the missed=4 recipe dropped
        assertEquals(listOf(0, 2, 3), out.map { it.missedIngredients.size })
        assertEquals(3, out.first().id)                    // ready-to-make recipe first
    }
}

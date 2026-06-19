package com.example.pantryparty

import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.ExtendedIngredient
import com.example.pantryparty.network.RecipeByIngredient
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

    private fun byIngredient(id: Int, missed: Int) =
        RecipeByIngredient(
            id = id, title = "R$id", image = null,
            usedIngredientCount = 0, missedIngredientCount = missed
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
    fun bucketAndSort_dropsOverThreeMissing_andSortsAscending() {
        // Recipe A: 2 missing, Recipe B: 0 missing, Recipe C: 4 missing (dropped).
        val empty = emptyList<PantryItem>()
        val a = RecipeMatcher.match(empty, recipe(ing(1, 1.0, "g"), ing(2, 1.0, "g")))
        val b = RecipeMatcher.match(
            listOf(pantry(3, 1, "g")),
            recipe(ing(3, 1.0, "g"))
        )
        val c = RecipeMatcher.match(
            empty,
            recipe(ing(1, 1.0, "g"), ing(2, 1.0, "g"), ing(3, 1.0, "g"), ing(4, 1.0, "g"))
        )

        val sorted = RecipeMatcher.bucketAndSort(listOf(a, b, c))

        assertEquals(2, sorted.size)               // C dropped (4 > MAX_MISSING)
        assertEquals(0, sorted[0].missingCount)    // B first
        assertEquals(2, sorted[1].missingCount)    // A second
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
        assertEquals(listOf(0, 2, 3), out.map { it.missedIngredientCount })
        assertEquals(3, out.first().id)                    // ready-to-make recipe first
    }
}

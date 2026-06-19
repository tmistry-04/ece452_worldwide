package com.example.pantryparty

import com.example.pantryparty.data.PantryItem
import com.example.pantryparty.network.ExtendedIngredient
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.recipe.PantryConsumer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryConsumerTest {

    // --- helpers --------------------------------------------------------------

    private fun pantry(id: Int, qty: Int, unit: String, name: String = "x") =
        PantryItem(id = id.toLong(), name = name, quantity = qty, unit = unit, spoonacularId = id)

    private fun ing(id: Int, amount: Double, unit: String, name: String = "x") =
        ExtendedIngredient(id = id, name = name, amount = amount, unit = unit, original = null, measures = null)

    private fun recipe(vararg ingredients: ExtendedIngredient) =
        RecipeInformation(
            id = 1, title = "Test", image = null, readyInMinutes = 10, servings = 2,
            extendedIngredients = ingredients.toList()
        )

    // --- tests ----------------------------------------------------------------

    @Test
    fun sameUnit_deductsAndKeepsRemainder() {
        // Have 5 eggs, recipe uses 2 -> 3 left, row updated (not deleted).
        val result = PantryConsumer.consume(
            pantry = listOf(pantry(1, qty = 5, unit = "piece", name = "egg")),
            recipe = recipe(ing(1, 2.0, "piece", name = "egg"))
        )
        assertEquals(1, result.toUpdate.size)
        assertEquals(3, result.toUpdate.single().quantity)
        assertTrue(result.toDelete.isEmpty())
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun depletedToZero_isDeleted() {
        // Have exactly 2, recipe uses 2 -> hits zero -> delete.
        val result = PantryConsumer.consume(
            pantry = listOf(pantry(1, qty = 2, unit = "cup")),
            recipe = recipe(ing(1, 2.0, "cup"))
        )
        assertTrue(result.toUpdate.isEmpty())
        assertEquals(1, result.toDelete.size)
        assertEquals(1L, result.toDelete.single().id)
    }

    @Test
    fun mismatchedUnit_isSkipped() {
        // Pantry in pieces, recipe wants grams -> no conversion -> untouched.
        val result = PantryConsumer.consume(
            pantry = listOf(pantry(1, qty = 5, unit = "piece", name = "apple")),
            recipe = recipe(ing(1, 100.0, "grams", name = "apple"))
        )
        assertTrue(result.toUpdate.isEmpty())
        assertTrue(result.toDelete.isEmpty())
        assertEquals(listOf("apple"), result.skipped)
    }

    @Test
    fun absentIngredient_isSkipped() {
        val result = PantryConsumer.consume(
            pantry = listOf(pantry(1, qty = 5, unit = "cup")),
            recipe = recipe(ing(99, 1.0, "cup", name = "saffron"))
        )
        assertTrue(result.toUpdate.isEmpty())
        assertTrue(result.toDelete.isEmpty())
        assertEquals(listOf("saffron"), result.skipped)
    }

    @Test
    fun mixedRecipe_splitsAcrossBuckets() {
        // egg: deduct, butter: deplete to 0, flour: wrong unit -> skip.
        val result = PantryConsumer.consume(
            pantry = listOf(
                pantry(1, qty = 5, unit = "piece", name = "egg"),
                pantry(2, qty = 1, unit = "tbsp", name = "butter"),
                pantry(3, qty = 2, unit = "piece", name = "flour")
            ),
            recipe = recipe(
                ing(1, 2.0, "piece", name = "egg"),
                ing(2, 1.0, "tbsp", name = "butter"),
                ing(3, 200.0, "grams", name = "flour")
            )
        )
        assertEquals(listOf(3), result.toUpdate.map { it.quantity })   // egg -> 3
        assertEquals(listOf("butter"), result.toDelete.map { it.name }) // butter gone
        assertEquals(listOf("flour"), result.skipped)                   // flour skipped
    }
}

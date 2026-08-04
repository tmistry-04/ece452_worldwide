package com.example.pantryparty

import com.example.pantryparty.network.ExtendedIngredient
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.pantry.StockItem
import com.example.pantryparty.recipe.IngredientStatus
import com.example.pantryparty.recipe.RecipeDetailRows
import com.example.pantryparty.recipe.RecipeMatcher
import com.example.pantryparty.recipe.StapleSet
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeDetailRowsTest {

    // Same fixture shape as RecipeMatcherTest: names default off the id so two
    // different ids are genuinely two different ingredients.
    private fun pantry(id: Int, qty: Int, unit: String, name: String = "ing$id") =
        StockItem(id = id.toLong(), spoonacularId = id, name = name, unit = unit, quantity = qty)

    private fun ing(id: Int, amount: Double, unit: String, name: String = "ing$id") =
        ExtendedIngredient(id = id, name = name, amount = amount, unit = unit, original = null, measures = null)

    private fun recipe(vararg ingredients: ExtendedIngredient) =
        RecipeInformation(id = 1, title = "Test", extendedIngredients = ingredients.toList())

    /** Runs the real matcher, then builds rows — the exact path the ViewModel takes. */
    private fun rowsFor(
        info: RecipeInformation,
        stock: List<StockItem>,
        nonStapleIds: Set<Int>? = info.extendedIngredients.map { it.id }.toSet(),
        alwaysHave: StapleSet = StapleSet.EMPTY
    ) = RecipeDetailRows.build(
        info = info,
        match = RecipeMatcher.match(stock, info, nonStapleIds, alwaysHave),
        staples = RecipeMatcher.staplesOf(stock, info, nonStapleIds, alwaysHave)
    )

    @Test
    fun rows_followTheRecipesOwnIngredientOrder() {
        // Interleaved on purpose: have, missing, have. The matcher returns these in
        // two separate buckets, and a recipe page must not reorder them.
        val info = recipe(ing(1, 2.0, "cup"), ing(2, 1.0, "g"), ing(3, 1.0, "cup"))
        val rows = rowsFor(info, listOf(pantry(1, qty = 3, unit = "cup"), pantry(3, qty = 5, unit = "cup")))

        assertEquals(listOf("ing1", "ing2", "ing3"), rows.map { it.required.name })
        assertEquals(
            listOf(IngredientStatus.HAVE, IngredientStatus.MISSING, IngredientStatus.HAVE),
            rows.map { it.status }
        )
    }

    @Test
    fun someOnHandButNotEnough_isShort_andAbsentIsMissing() {
        val info = recipe(ing(1, 5.0, "cup"), ing(2, 1.0, "cup"))
        val rows = rowsFor(info, listOf(pantry(1, qty = 2, unit = "cup")))

        val short = rows.single { it.required.id == 1 }
        assertEquals(IngredientStatus.SHORT, short.status)
        assertEquals(2, short.haveQuantity)
        assertEquals("cup", short.haveUnit)

        val absent = rows.single { it.required.id == 2 }
        assertEquals(IngredientStatus.MISSING, absent.status)
        // Nothing on hand means there is no "have 0 cup" to show.
        assertEquals(null, absent.haveQuantity)
    }

    @Test
    fun staplesAreMarked_andExcludedFromBothHalvesOfTheCount() {
        val info = recipe(ing(1, 2.0, "cup"), ing(99, 1.0, "tsp", name = "salt"))
        // ing 99 is outside nonStapleIds and untracked -> Spoonacular's staple guess.
        val rows = rowsFor(info, listOf(pantry(1, qty = 3, unit = "cup")), nonStapleIds = setOf(1))

        assertEquals(IngredientStatus.STAPLE, rows.single { it.required.id == 99 }.status)
        assertEquals(1, RecipeDetailRows.haveCount(rows))
        assertEquals(1, RecipeDetailRows.checkedCount(rows))
    }

    @Test
    fun duplicateIngredientLines_eachGetTheirOwnRow() {
        // Spoonacular repeats a line verbatim when a recipe has sections. These two
        // are equal by value, so a value-keyed map would collapse them into one row.
        val salt = ing(9, 1.0, "tsp", name = "salt")
        val saltAgain = ing(9, 1.0, "tsp", name = "salt")
        val info = recipe(salt, saltAgain)

        val rows = rowsFor(info, stock = emptyList())

        assertEquals(2, rows.size)
        assertEquals(listOf(IngredientStatus.MISSING, IngredientStatus.MISSING), rows.map { it.status })
    }

    @Test
    fun everyIngredientProducesExactlyOneRow() {
        val info = recipe(ing(1, 1.0, "cup"), ing(2, 1.0, "cup"), ing(3, 1.0, "cup"))
        val rows = rowsFor(info, listOf(pantry(1, qty = 5, unit = "cup")))
        assertEquals(info.extendedIngredients.size, rows.size)
    }
}

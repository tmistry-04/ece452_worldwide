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
    fun inPantrySameUnit_suggestsRecipeAmount() {
        // Have 5 eggs, recipe uses 2 -> one deductible line suggesting 2.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1, qty = 5, unit = "piece", name = "egg")),
            recipe = recipe(ing(1, 2.0, "piece", name = "egg"))
        )
        assertEquals(1, plan.lines.size)
        assertEquals(2, plan.lines.single().suggested)
        assertEquals("egg", plan.lines.single().item.name)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun inPantryDifferentUnit_isEditableStartingAtZero() {
        // Pantry in pieces, recipe wants grams. We can't convert, but since the
        // deduction is manual the ingredient is still an editable line (starts at 0,
        // in pantry units) rather than being skipped.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1, qty = 5, unit = "piece", name = "apple")),
            recipe = recipe(ing(1, 100.0, "grams", name = "apple"))
        )
        assertEquals(1, plan.lines.size)
        assertEquals(0, plan.lines.single().suggested)
        assertEquals("piece", plan.lines.single().unit)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun suggestionNeverExceedsWhatIsOnHand() {
        // Recipe wants 5 but only 2 on hand (same unit) -> suggested clamps to 2.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1, qty = 2, unit = "cup")),
            recipe = recipe(ing(1, 5.0, "cup"))
        )
        assertEquals(2, plan.lines.single().suggested)
    }

    @Test
    fun fractionalAmount_isRoundedToWholeUnits() {
        // 2.6 cups rounds to 3; 2.4 would round to 2.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1, qty = 10, unit = "cup")),
            recipe = recipe(ing(1, 2.6, "cup"))
        )
        assertEquals(3, plan.lines.single().suggested)
    }

    @Test
    fun absentIngredient_isSkipped() {
        // A non-staple the recipe needs but the pantry lacks -> nothing to deduct.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1, qty = 5, unit = "cup")),
            recipe = recipe(ing(99, 1.0, "cup", name = "saffron"))
        )
        assertTrue(plan.lines.isEmpty())
        assertEquals(listOf("saffron"), plan.skipped)
    }

    @Test
    fun staples_areOmittedEntirely() {
        // sugar (id 2) isn't in the non-staple set -> left out of the plan: neither
        // deducted nor listed as skipped, even though it's absent from the pantry.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1, qty = 5, unit = "piece", name = "egg")),
            recipe = recipe(
                ing(1, 2.0, "piece", name = "egg"),
                ing(2, 1.0, "cup", name = "sugar")
            ),
            nonStapleIds = setOf(1)
        )
        assertEquals(listOf("egg"), plan.lines.map { it.item.name })
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun mixedRecipe_everyPantryItemIsDeductible_onlyAbsentIsSkipped() {
        // egg + butter (same unit) prefill from the recipe; flour (wrong unit, but in
        // pantry) is an editable line starting at 0; saffron (absent) is skipped.
        val plan = PantryConsumer.plan(
            pantry = listOf(
                pantry(1, qty = 5, unit = "piece", name = "egg"),
                pantry(2, qty = 1, unit = "tbsp", name = "butter"),
                pantry(3, qty = 2, unit = "piece", name = "flour")
            ),
            recipe = recipe(
                ing(1, 2.0, "piece", name = "egg"),
                ing(2, 1.0, "tbsp", name = "butter"),
                ing(3, 200.0, "grams", name = "flour"),
                ing(99, 1.0, "cup", name = "saffron")
            )
        )
        assertEquals(listOf("egg", "butter", "flour"), plan.lines.map { it.item.name })
        assertEquals(listOf(2, 1, 0), plan.lines.map { it.suggested })
        assertEquals(listOf("saffron"), plan.skipped)
    }
}

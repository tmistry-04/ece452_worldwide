package com.example.pantryparty

import com.example.pantryparty.pantry.StockItem
import com.example.pantryparty.network.ExtendedIngredient
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.recipe.PantryConsumer
import com.example.pantryparty.recipe.RecipeMatcher
import com.example.pantryparty.recipe.StapleSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryConsumerTest {

    // --- helpers --------------------------------------------------------------

    // Names default off the id so two different ids are two different ingredients:
    // matching falls back to the name, so a shared default would silently make
    // unrelated fixtures match each other.
    private fun pantry(id: Int, qty: Int, unit: String, name: String = "ing$id") =
        StockItem(id = id.toLong(), spoonacularId = id, name = name, unit = unit, quantity = qty)

    private fun ing(id: Int, amount: Double, unit: String, name: String = "ing$id") =
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
    fun inPantryDifferentDimension_isEditableStartingAtZero() {
        // Both units are known, but cups measure volume and grams measure weight.
        // No density to convert with -> the line still starts at 0.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1, qty = 5, unit = "cup", name = "flour")),
            recipe = recipe(ing(1, 200.0, "grams", name = "flour"))
        )
        assertEquals(0, plan.lines.single().suggested)
        assertEquals("cup", plan.lines.single().unit)
    }

    @Test
    fun convertibleWeight_prefillsInPantryUnits() {
        // Pantry in kg, recipe in g: 2000 g -> 2 kg.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1, qty = 5, unit = "kg", name = "flour")),
            recipe = recipe(ing(1, 2000.0, "g", name = "flour"))
        )
        assertEquals(2, plan.lines.single().suggested)
        assertEquals("kg", plan.lines.single().unit)
    }

    @Test
    fun convertibleWeight_prefillsInTheOtherDirection() {
        // Pantry in g, recipe in kg: 1.5 kg -> 1500 g.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1, qty = 5000, unit = "g", name = "flour")),
            recipe = recipe(ing(1, 1.5, "kg", name = "flour"))
        )
        assertEquals(1500, plan.lines.single().suggested)
    }

    @Test
    fun convertibleVolume_prefillsRounded() {
        // Pantry in cups, recipe in ml: 500 ml is 2.11 cups -> rounds to 2.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1, qty = 4, unit = "cup", name = "milk")),
            recipe = recipe(ing(1, 500.0, "ml", name = "milk"))
        )
        assertEquals(2, plan.lines.single().suggested)
    }

    @Test
    fun convertibleVolume_prefillsRoundedInTheOtherDirection() {
        // Pantry in ml, recipe in cups: 2 cups is 473.18 ml -> rounds to 473.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1, qty = 1000, unit = "ml", name = "milk")),
            recipe = recipe(ing(1, 2.0, "cup", name = "milk"))
        )
        assertEquals(473, plan.lines.single().suggested)
    }

    @Test
    fun convertedSuggestionNeverExceedsWhatIsOnHand() {
        // 1 lb is 454 g but only 300 g on hand -> clamps to 300.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1, qty = 300, unit = "g", name = "butter")),
            recipe = recipe(ing(1, 1.0, "lb", name = "butter"))
        )
        assertEquals(300, plan.lines.single().suggested)
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

    // --- id/name matching -----------------------------------------------------

    @Test
    fun differentIdSameName_isDeductibleNotSkipped() {
        // Real values from recipe 639637: pantry butter is 1001 (autocomplete),
        // the recipe asks for 1145. It must produce a line, not land in `skipped`.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1001, qty = 1000, unit = "g", name = "butter")),
            recipe = recipe(ing(1145, 50.0, "g", name = "butter"))
        )
        assertTrue(plan.skipped.isEmpty())
        assertEquals(50, plan.lines.single().suggested)
        assertEquals(1001, plan.lines.single().item.spoonacularId)
    }

    @Test
    fun twoIngredientsResolvingToOneRow_produceOneLine() {
        // A recipe listing both "butter" (1001) and "unsalted butter" (1145) now
        // resolves both to the same pantry row. One line only, so confirming the
        // dialog can't deduct that row twice.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(1001, qty = 1000, unit = "g", name = "butter")),
            recipe = recipe(
                ing(1001, 30.0, "g", name = "butter"),
                ing(1145, 50.0, "g", name = "butter")
            )
        )
        assertEquals(1, plan.lines.size)
        assertEquals(30, plan.lines.single().suggested)   // the first listing wins
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun matchAndPlanAgreeOnWhetherAnIngredientIsInThePantry() {
        // The two paths take the same pantry, recipe and staple set, so an
        // ingredient must never be "short on" in one and "not in your pantry" in
        // the other. This is the inconsistency that hid the id mismatch.
        val pantryRows = listOf(
            pantry(1001, qty = 1000, unit = "g", name = "butter"),
            pantry(20129, qty = 2, unit = "cup", name = "self raising flour")
        )
        val r = recipe(
            ing(1145, 50.0, "g", name = "butter"),
            ing(20129, 2.0, "cups", name = "self raising flour"),
            ing(1077, 0.75, "cup", name = "milk")
        )

        val match = RecipeMatcher.match(pantryRows, r)
        val plan = PantryConsumer.plan(pantryRows, r)

        val absentInMatch = match.missing.filter { it.haveQuantity == null }.map { it.required.name }
        assertEquals(absentInMatch, plan.skipped)
        assertEquals(listOf("milk"), plan.skipped)
    }

    // --- staples --------------------------------------------------------------

    @Test
    fun alwaysHaveEntry_getsNoDeductionLine() {
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(2047, qty = 500, unit = "g", name = "salt")),
            recipe = recipe(ing(2047, 1.0, "tsp", name = "salt")),
            nonStapleIds = setOf(2047),
            alwaysHave = StapleSet.of(listOf(2047 to "salt"))
        )
        // Declared always-have wins even over a tracked row: nothing to deduct, and
        // it isn't reported as missing either.
        assertTrue(plan.lines.isEmpty())
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun trackedIngredient_getsADeductionLineDespiteSpoonacular() {
        // The flour case: Spoonacular calls it a staple, but it's tracked, so cooking
        // must actually deduct it.
        val plan = PantryConsumer.plan(
            pantry = listOf(pantry(20129, qty = 5, unit = "cup", name = "self raising flour")),
            recipe = recipe(ing(20129, 2.0, "cups", name = "self raising flour")),
            nonStapleIds = setOf(1145)
        )
        assertEquals(1, plan.lines.size)
        assertEquals(2, plan.lines.single().suggested)
    }

    @Test
    fun matchAndPlanAgreeOnStapleClassification() {
        val pantryRows = listOf(pantry(20129, qty = 5, unit = "cup", name = "self raising flour"))
        val r = recipe(
            ing(20129, 2.0, "cups", name = "self raising flour"),
            ing(2047, 1.0, "tsp", name = "salt"),
            ing(1145, 50.0, "g", name = "butter")
        )
        val alwaysHave = StapleSet.of(listOf(2047 to "salt"))
        val nonStaple = setOf(20129)

        val match = RecipeMatcher.match(pantryRows, r, nonStaple, alwaysHave)
        val plan = PantryConsumer.plan(pantryRows, r, nonStaple, alwaysHave)

        // Only flour is classified by either path; salt and butter are staples in both.
        assertEquals(listOf("self raising flour"), plan.lines.map { it.item.name })
        assertTrue(plan.skipped.isEmpty())
        assertEquals(1, match.available.size + match.missing.size)
    }
}

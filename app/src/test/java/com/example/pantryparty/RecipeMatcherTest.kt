package com.example.pantryparty

import com.example.pantryparty.pantry.StockItem
import com.example.pantryparty.network.ExtendedIngredient
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.RecipeIngredientBrief
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.recipe.RecipeMatcher
import com.example.pantryparty.recipe.StapleSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeMatcherTest {

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
        // "piece" isn't a convertible measure, so there's no way to compare it with
        // cups -> fall back to presence and treat the ingredient as available.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1, qty = 1, unit = "piece")),
            recipe = recipe(ing(1, 2.0, "cup"))
        )
        assertEquals(0, match.missingCount)
        assertTrue(match.canMake)
    }

    @Test
    fun differentDimensions_fallBackToPresentAndAvailable() {
        // Both units are known, but grams measure weight and cups measure volume.
        // Without a density there's nothing to compare -> presence fallback.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1, qty = 1, unit = "grams")),
            recipe = recipe(ing(1, 2.0, "cup"))
        )
        assertEquals(0, match.missingCount)
        assertTrue(match.canMake)
    }

    @Test
    fun convertibleWeight_sufficient_isAvailable() {
        // 1 kg on hand, recipe wants 500 g -> converts to 1000 g >= 500 g.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1, qty = 1, unit = "kg")),
            recipe = recipe(ing(1, 500.0, "g"))
        )
        assertEquals(0, match.missingCount)
        assertTrue(match.canMake)
        assertEquals(1, match.available.size)
    }

    @Test
    fun convertibleWeight_insufficient_isMissing() {
        // The other direction: 100 g on hand, recipe wants 1 lb (453.6 g) -> short.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1, qty = 100, unit = "g")),
            recipe = recipe(ing(1, 1.0, "lb"))
        )
        assertEquals(1, match.missingCount)
        assertFalse(match.canMake)
        assertEquals(100, match.missing.single().haveQuantity)
        assertEquals("g", match.missing.single().haveUnit)
    }

    @Test
    fun convertibleVolume_sufficient_isAvailable() {
        // 2 cups on hand (473 ml), recipe wants 250 ml.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1, qty = 2, unit = "cup")),
            recipe = recipe(ing(1, 250.0, "ml"))
        )
        assertEquals(0, match.missingCount)
        assertTrue(match.canMake)
    }

    @Test
    fun convertibleVolume_insufficient_isMissing() {
        // The other direction: 2 tbsp on hand (29.6 ml), recipe wants 1 cup (236.6 ml).
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1, qty = 2, unit = "tbsp")),
            recipe = recipe(ing(1, 1.0, "cup"))
        )
        assertEquals(1, match.missingCount)
        assertFalse(match.canMake)
        assertEquals(2, match.missing.single().haveQuantity)
    }

    @Test
    fun unitAliasesAndCasing_areRecognized() {
        // Spoonacular spells units out and pluralizes them; 1 Litre >= 500 millilitres.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1, qty = 1, unit = "Litres")),
            recipe = recipe(ing(1, 500.0, "milliliters"))
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
            pantry = emptyList(),
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

    // --- id/name matching -----------------------------------------------------

    @Test
    fun differentIdSameName_matchesOnName() {
        // Real values from recipe 639637 ("Classic scones"): autocomplete gives a
        // butter pantry row id 1001, but the recipe asks for 1145 ("unsalted
        // butter"). Same canonical name, so 1000 g on hand covers the 50 g needed.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1001, qty = 1000, unit = "g", name = "butter")),
            recipe = recipe(ing(1145, 50.0, "g", name = "butter"))
        )
        assertEquals(0, match.missingCount)
        assertTrue(match.canMake)
        assertEquals(1001, match.available.single().pantryItem.spoonacularId)
    }

    @Test
    fun differentIdSameName_stillComparesAmounts() {
        // The name fallback resolves the row; sufficiency is still a real check.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1001, qty = 10, unit = "g", name = "butter")),
            recipe = recipe(ing(1145, 50.0, "g", name = "butter"))
        )
        assertEquals(1, match.missingCount)
        assertEquals(10, match.missing.single().haveQuantity)
    }

    @Test
    fun differentIdDifferentName_staysMissing() {
        // The fallback is exact-match on the name, never a substring — holding
        // peanut butter must not satisfy a recipe that wants butter.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1001, qty = 1000, unit = "g", name = "peanut butter")),
            recipe = recipe(ing(1145, 50.0, "g", name = "butter"))
        )
        assertEquals(1, match.missingCount)
        assertEquals(null, match.missing.single().haveQuantity)
    }

    @Test
    fun nameMatchIsCaseAndWhitespaceInsensitive() {
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1001, qty = 500, unit = "g", name = "  Heavy   Cream ")),
            recipe = recipe(ing(1145, 50.0, "g", name = "heavy cream"))
        )
        assertEquals(0, match.missingCount)
    }

    // --- staples --------------------------------------------------------------

    @Test
    fun trackedIngredient_isNeverAStapleEvenWhenSpoonacularSaysSo() {
        // The real misclassification: for recipe 639637 Spoonacular reports self
        // raising flour (20129) outside used∪missed, i.e. a staple. Tracking it means
        // the user wants it counted, so it must be amount-checked — and it's short.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(20129, qty = 1, unit = "cup", name = "self raising flour")),
            recipe = recipe(ing(20129, 2.0, "cups", name = "self raising flour")),
            nonStapleIds = setOf(1145)          // flour deliberately absent
        )
        assertEquals(1, match.missingCount)
        assertEquals(1, match.missing.single().haveQuantity)
    }

    @Test
    fun trackedIngredient_unStaplesViaNameNotJustId() {
        // Pantry butter is 1001, the recipe's is 1145. Catalog membership has to be
        // resolved by name too, or the un-staple rule silently fails to fire.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1001, qty = 10, unit = "g", name = "butter")),
            recipe = recipe(ing(1145, 50.0, "g", name = "butter")),
            nonStapleIds = setOf(999)           // 1145 would otherwise be a staple
        )
        assertEquals(1, match.missingCount)
        assertEquals(10, match.missing.single().haveQuantity)
    }

    @Test
    fun alwaysHaveEntry_isAStapleEvenThoughItIsNotTracked() {
        val match = RecipeMatcher.match(
            pantry = emptyList(),
            recipe = recipe(ing(2047, 1.0, "tsp", name = "salt")),
            nonStapleIds = setOf(2047),         // Spoonacular says check it
            alwaysHave = StapleSet.of(listOf(2047 to "salt"))
        )
        assertEquals(0, match.missingCount)
        assertTrue(match.canMake)
    }

    @Test
    fun alwaysHaveEntry_matchesByNameAcrossDifferentIds() {
        // Saved from autocomplete as 2047; the recipe happens to use another id.
        val match = RecipeMatcher.match(
            pantry = emptyList(),
            recipe = recipe(ing(1102047, 1.0, "tsp", name = "salt")),
            nonStapleIds = setOf(1102047),
            alwaysHave = StapleSet.of(listOf(2047 to "Salt"))
        )
        assertEquals(0, match.missingCount)
    }

    @Test
    fun alwaysHaveEntry_doesNotSwallowADifferentIngredient() {
        val match = RecipeMatcher.match(
            pantry = emptyList(),
            recipe = recipe(ing(1102047, 1.0, "tsp", name = "celery salt")),
            nonStapleIds = setOf(1102047),
            alwaysHave = StapleSet.of(listOf(2047 to "salt"))
        )
        assertEquals(1, match.missingCount)
    }

    @Test
    fun untrackedAndUnlisted_stillFollowsSpoonacular() {
        // Existing behavior preserved: not tracked, not declared -> staple.
        val match = RecipeMatcher.match(
            pantry = emptyList(),
            recipe = recipe(ing(2047, 1.0, "tsp", name = "salt")),
            nonStapleIds = setOf(1145)
        )
        assertEquals(0, match.missingCount)
    }

    @Test
    fun staplesOf_agreesWithWhatMatchSkips() {
        // The card's staple list must name exactly the ingredients the amount check
        // skipped, or it advertises staples that actually get checked.
        val pantryRows = listOf(pantry(20129, qty = 5, unit = "cup", name = "self raising flour"))
        val r = recipe(
            ing(20129, 2.0, "cups", name = "self raising flour"),   // tracked -> checked
            ing(2047, 1.0, "tsp", name = "salt"),                   // declared -> staple
            ing(1145, 50.0, "g", name = "butter")                   // untracked -> staple
        )
        val alwaysHave = StapleSet.of(listOf(2047 to "salt"))
        val nonStaple = setOf(20129)

        val staples = RecipeMatcher.staplesOf(pantryRows, r, nonStaple, alwaysHave)
        val match = RecipeMatcher.match(pantryRows, r, nonStaple, alwaysHave)

        assertEquals(listOf("salt", "butter"), staples.map { it.name })
        // Everything the recipe needs is either a staple or was classified by match.
        assertEquals(
            r.extendedIngredients.size,
            staples.size + match.available.size + match.missing.size
        )
    }

    @Test
    fun blankPantryName_isNeverAMatchAllKey() {
        // A nameless pantry row must not absorb every unmatched ingredient.
        val match = RecipeMatcher.match(
            pantry = listOf(pantry(1001, qty = 1000, unit = "g", name = "")),
            recipe = recipe(ing(1145, 50.0, "g", name = ""))
        )
        assertEquals(1, match.missingCount)
        assertEquals(null, match.missing.single().haveQuantity)
    }
}

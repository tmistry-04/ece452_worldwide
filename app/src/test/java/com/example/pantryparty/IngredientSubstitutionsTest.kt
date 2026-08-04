package com.example.pantryparty

import com.example.pantryparty.network.IngredientSubstitutes
import com.example.pantryparty.network.SpoonacularJson
import com.example.pantryparty.recipe.IngredientSubstitutions
import com.example.pantryparty.recipe.SubstituteResult
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientSubstitutionsTest {

    /**
     * The trap this whole class exists for: a miss is HTTP 200 with
     * `status: "failure"` and no `substitutes` key at all. Without defaults on every
     * field, this response wouldn't parse.
     */
    @Test
    fun theFailureShapeParses_despiteMissingKeys() {
        val response = SpoonacularJson.decodeFromString<IngredientSubstitutes>(
            """{"status":"failure","message":"Could not find any substitutes for that ingredient."}"""
        )
        assertEquals("failure", response.status)
        assertTrue(response.substitutes.isEmpty())
        assertEquals("", response.ingredient)
    }

    @Test
    fun theSuccessShapeParses() {
        val response = SpoonacularJson.decodeFromString<IngredientSubstitutes>(
            """
            {"status":"success","ingredient":"butter",
             "substitutes":["1 cup = 1 cup margarine","1 cup = 7/8 cup vegetable oil + 1/2 tsp salt"],
             "message":"Found 2 substitutes for the ingredient."}
            """
        )
        assertEquals(2, response.substitutes.size)
        assertEquals("butter", response.ingredient)
    }

    @Test
    fun readTreatsStatusFailureAsNotFound_notAnError() {
        val result = IngredientSubstitutions.read(
            IngredientSubstitutes(status = "failure", message = "Could not find any substitutes."),
            requested = "peanuts"
        )
        assertEquals("Could not find any substitutes.", (result as SubstituteResult.NotFound).message)
    }

    @Test
    fun readReturnsTheOptionsOnSuccess() {
        val result = IngredientSubstitutions.read(
            IngredientSubstitutes(status = "success", substitutes = listOf("1 cup = 1 cup margarine")),
            requested = "butter"
        )
        assertEquals(listOf("1 cup = 1 cup margarine"), (result as SubstituteResult.Found).options)
    }

    @Test
    fun aSuccessCarryingNoOptions_isStillNotFound() {
        val result = IngredientSubstitutions.read(
            IngredientSubstitutes(status = "success", substitutes = listOf("", "   ")),
            requested = "butter"
        )
        assertTrue(result is SubstituteResult.NotFound)
    }

    @Test
    fun anUnrecognisedShapeFailsClosed() {
        // Default status is "" — reads as "none found", never as a fabricated success.
        val result = IngredientSubstitutions.read(IngredientSubstitutes(), requested = "peanuts")
        assertEquals("No substitutes known for peanuts.", (result as SubstituteResult.NotFound).message)
    }

    @Test
    fun trimsWhitespaceOffOptions() {
        val result = IngredientSubstitutions.read(
            IngredientSubstitutes(status = "SUCCESS", substitutes = listOf("  1 cup = 1 cup margarine  ")),
            requested = "butter"
        )
        assertEquals(listOf("1 cup = 1 cup margarine"), (result as SubstituteResult.Found).options)
    }
}

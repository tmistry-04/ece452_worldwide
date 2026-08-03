package com.example.pantryparty

import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.network.SpoonacularJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the parser configuration: optional fields the API omits must fall back
 * to the models' defaults (never null), and unknown response fields must be
 * ignored rather than fail the whole parse.
 */
class SpoonacularModelsTest {

    @Test
    fun recipeByIngredient_missingListsFallBackToEmpty() {
        val recipe = SpoonacularJson.decodeFromString<RecipeByIngredient>(
            """{"id": 7, "title": "Toast"}"""
        )
        assertTrue(recipe.usedIngredients.isEmpty())
        assertTrue(recipe.missedIngredients.isEmpty())
        assertNull(recipe.image)
    }

    @Test
    fun autocomplete_missingOptionalFieldsUseDefaults() {
        val result = SpoonacularJson.decodeFromString<List<IngredientAutocomplete>>(
            """[{"id": 1, "name": "apple"}]"""
        )
        val apple = result.single()
        assertTrue(apple.possibleUnits.isEmpty())
        assertNull(apple.image)
        assertNull(apple.aisle)
    }

    @Test
    fun unknownFieldsFromTheApi_areIgnored() {
        val info = SpoonacularJson.decodeFromString<RecipeInformation>(
            """
            {
              "id": 3, "title": "Soup", "someNewField": {"x": 1},
              "extendedIngredients": [
                {"id": 9, "name": "salt", "amount": 1.0, "unit": "tsp", "brandNewFlag": true}
              ]
            }
            """
        )
        assertEquals("salt", info.extendedIngredients.single().name)
    }

    @Test
    fun explicitNulls_coerceToDefaults() {
        val recipe = SpoonacularJson.decodeFromString<RecipeByIngredient>(
            """{"id": 7, "title": "Toast", "usedIngredients": null, "image": null}"""
        )
        assertTrue(recipe.usedIngredients.isEmpty())
        assertNull(recipe.image)
    }
}

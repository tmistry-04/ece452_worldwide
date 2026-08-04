package com.example.pantryparty

import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.network.SpoonacularJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // --- Detail-page fields -------------------------------------------------
    // These already came back from informationBulk before the models declared
    // them; the tests below pin that they parse and that adding them can't break
    // a response that omits them.

    @Test
    fun recipeInformation_detailFieldsParse() {
        val info = SpoonacularJson.decodeFromString<RecipeInformation>(
            """
            {
              "id": 42, "title": "Banana Dip", "readyInMinutes": 45, "servings": 4,
              "glutenFree": true, "vegetarian": true,
              "diets": ["gluten free", "lacto ovo vegetarian"],
              "dishTypes": ["dessert", "snack"],
              "cuisines": ["American"],
              "sourceUrl": "https://example.com/dip",
              "sourceName": "Example Kitchen",
              "instructions": "<p>Mash the bananas.</p>",
              "analyzedInstructions": [
                {
                  "name": "",
                  "steps": [
                    {"number": 1, "step": "Peel the bananas.",
                     "ingredients": [{"id": 9040, "name": "banana", "localizedName": "banana"}],
                     "equipment": []},
                    {"number": 2, "step": "Melt the chocolate.",
                     "ingredients": [], "equipment": [{"id": 404645, "name": "bowl", "localizedName": "bowl"}],
                     "length": {"number": 5, "unit": "minutes"}}
                  ]
                }
              ]
            }
            """
        )
        assertEquals(45, info.readyInMinutes)
        assertEquals(4, info.servings)
        assertTrue(info.glutenFree)
        assertTrue(info.vegetarian)
        assertEquals(listOf("gluten free", "lacto ovo vegetarian"), info.diets)
        assertEquals(listOf("dessert", "snack"), info.dishTypes)
        assertEquals(listOf("American"), info.cuisines)
        assertEquals("https://example.com/dip", info.sourceUrl)

        val steps = info.analyzedInstructions.single().steps
        assertEquals(2, steps.size)
        assertEquals("Peel the bananas.", steps[0].step)
        assertEquals("banana", steps[0].ingredients.single().name)
        assertEquals("bowl", steps[1].equipment.single().name)
        assertEquals(5, steps[1].length?.number)
    }

    @Test
    fun recipeInformation_detailFieldsFallBackToDefaultsWhenAbsent() {
        val info = SpoonacularJson.decodeFromString<RecipeInformation>(
            """{"id": 1, "title": "Toast"}"""
        )
        assertTrue(info.analyzedInstructions.isEmpty())
        assertTrue(info.diets.isEmpty())
        assertTrue(info.dishTypes.isEmpty())
        assertTrue(info.cuisines.isEmpty())
        assertFalse(info.glutenFree)
        assertFalse(info.vegetarian)
        assertNull(info.instructions)
        assertNull(info.sourceUrl)
    }

    @Test
    fun recipeInformation_explicitNullDetailFields_coerceToDefaults() {
        val info = SpoonacularJson.decodeFromString<RecipeInformation>(
            """
            {"id": 1, "title": "Toast", "diets": null, "glutenFree": null,
             "analyzedInstructions": null, "dishTypes": null}
            """
        )
        assertTrue(info.diets.isEmpty())
        assertTrue(info.dishTypes.isEmpty())
        assertTrue(info.analyzedInstructions.isEmpty())
        assertFalse(info.glutenFree)
    }

    @Test
    fun analyzedInstructions_unknownStepFields_areIgnored() {
        val info = SpoonacularJson.decodeFromString<RecipeInformation>(
            """
            {
              "id": 1, "title": "Toast",
              "analyzedInstructions": [
                {"name": "", "steps": [{"number": 1, "step": "Toast it.", "brandNewFlag": true}]}
              ]
            }
            """
        )
        assertEquals("Toast it.", info.analyzedInstructions.single().steps.single().step)
    }
}

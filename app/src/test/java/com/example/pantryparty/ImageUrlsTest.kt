package com.example.pantryparty

import com.example.pantryparty.ui.ingredientImageUrl
import com.example.pantryparty.ui.recipeImageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlsTest {

    @Test
    fun recipeUrl_prefixesTheBareFilenameSimilarReturns() {
        // recipes/{id}/similar hands back a filename, unlike every other endpoint.
        assertEquals(
            "https://img.spoonacular.com/recipes/Chicken-Verde-638409.jpg",
            recipeImageUrl("Chicken-Verde-638409.jpg")
        )
    }

    @Test
    fun recipeUrl_passesAbsoluteUrlsThrough() {
        val absolute = "https://img.spoonacular.com/recipes/715394-556x370.jpg"
        assertEquals(absolute, recipeImageUrl(absolute))
    }

    @Test
    fun recipeUrl_isNullForNothing() {
        assertNull(recipeImageUrl(null))
        assertNull(recipeImageUrl(""))
        assertNull(recipeImageUrl("   "))
    }

    @Test
    fun ingredientUrl_stillPrefixesAndPassesThrough() {
        assertEquals(
            "https://img.spoonacular.com/ingredients_100x100/apple.jpg",
            ingredientImageUrl("apple.jpg")
        )
        assertEquals("https://example.com/x.jpg", ingredientImageUrl("https://example.com/x.jpg"))
        assertNull(ingredientImageUrl(null))
    }

    @Test
    fun theTwoBasesAreDifferent() {
        // Routing a recipe image through the ingredient helper would 404.
        assertEquals(
            false,
            recipeImageUrl("x.jpg") == ingredientImageUrl("x.jpg")
        )
    }
}

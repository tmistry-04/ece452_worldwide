package com.example.pantryparty

import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.recipe.RecipeBadges
import com.example.pantryparty.recipe.RecipeFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeBadgesTest {

    private fun info(
        vegetarian: Boolean = false,
        vegan: Boolean = false,
        glutenFree: Boolean = false,
        dairyFree: Boolean = false,
        diets: List<String> = emptyList(),
        dishTypes: List<String> = emptyList(),
        cuisines: List<String> = emptyList()
    ) = RecipeInformation(
        id = 1, title = "Test",
        vegetarian = vegetarian, vegan = vegan, glutenFree = glutenFree, dairyFree = dairyFree,
        diets = diets, dishTypes = dishTypes, cuisines = cuisines
    )

    @Test
    fun booleanFlags_becomeLabels() {
        assertEquals(
            listOf("Gluten Free", "Dairy Free", "Vegetarian"),
            RecipeBadges.diets(info(glutenFree = true, dairyFree = true, vegetarian = true))
        )
    }

    @Test
    fun dietsArray_isDedupedAgainstTheBooleans() {
        // glutenFree = true and diets = ["gluten free"] describe the same fact.
        assertEquals(
            listOf("Gluten Free"),
            RecipeBadges.diets(info(glutenFree = true, diets = listOf("gluten free")))
        )
    }

    @Test
    fun apiDietAliases_mapToTheFilterPanelWording() {
        assertEquals(
            listOf("Vegetarian", "Low FODMAP", "Whole30", "Paleo", "Pescetarian"),
            RecipeBadges.diets(
                info(diets = listOf("lacto ovo vegetarian", "fodmap friendly", "whole 30", "paleolithic", "pescatarian"))
            )
        )
    }

    @Test
    fun everyLabelThisProduces_isWordedLikeTheFilterPanel() {
        // A badge must read the same as the filter the user checked, or the two
        // screens appear to disagree about the same recipe.
        val produced = RecipeBadges.diets(
            info(diets = RecipeFilters.DIETS.map { it.lowercase() })
        )
        assertTrue(
            "unexpected labels: ${produced - RecipeFilters.DIETS.toSet()}",
            RecipeFilters.DIETS.containsAll(produced)
        )
    }

    @Test
    fun unknownDietString_isTitleCasedAndKept() {
        assertEquals(listOf("Brand New Diet"), RecipeBadges.diets(info(diets = listOf("brand new diet"))))
    }

    @Test
    fun noFlagsAndNoArrays_yieldNoBadges() {
        assertTrue(RecipeBadges.diets(info()).isEmpty())
        assertTrue(RecipeBadges.categories(info()).isEmpty())
    }

    @Test
    fun categories_mergeDishTypesAndCuisines_deduped() {
        assertEquals(
            listOf("Dessert", "Snack", "American"),
            RecipeBadges.categories(
                info(dishTypes = listOf("dessert", "snack"), cuisines = listOf("American", "american"))
            )
        )
    }
}

package com.example.pantryparty.fakes

import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.network.SpoonacularRepository
import kotlinx.coroutines.awaitCancellation

/**
 * Scriptable SpoonacularRepository: each test sets the result it wants returned.
 * The `hang*` flags park the call until it is cancelled, simulating a request
 * that is still in flight.
 */
class FakeSpoonacularRepository : SpoonacularRepository {

    var autocompleteResult: Result<List<IngredientAutocomplete>> = Result.success(emptyList())
    var recipesResult: Result<List<RecipeByIngredient>> = Result.success(emptyList())
    var detailsResult: Result<List<RecipeInformation>> = Result.success(emptyList())

    var hangAutocomplete = false
    var hangRecipes = false

    var autocompleteCalls = 0
        private set
    var lastRecipeQuery: List<String>? = null
        private set

    override suspend fun autocompleteIngredients(query: String): Result<List<IngredientAutocomplete>> {
        autocompleteCalls++
        if (hangAutocomplete) awaitCancellation()
        return autocompleteResult
    }

    override suspend fun findRecipesByIngredients(
        names: List<String>,
        number: Int
    ): Result<List<RecipeByIngredient>> {
        lastRecipeQuery = names
        if (hangRecipes) awaitCancellation()
        return recipesResult
    }

    override suspend fun getRecipeInformationBulk(ids: List<Int>): Result<List<RecipeInformation>> =
        detailsResult
}

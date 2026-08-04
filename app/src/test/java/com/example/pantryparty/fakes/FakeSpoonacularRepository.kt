package com.example.pantryparty.fakes

import com.example.pantryparty.network.IngredientAutocomplete
import com.example.pantryparty.network.IngredientSubstitutes
import com.example.pantryparty.network.RecipeByIngredient
import com.example.pantryparty.network.RecipeInformation
import com.example.pantryparty.network.SimilarRecipe
import com.example.pantryparty.network.SpoonacularRepository
import kotlinx.coroutines.awaitCancellation

/**
 * Scriptable SpoonacularRepository: each test sets the result it wants returned.
 * Every endpoint is instrumented the same way — a call counter, the last
 * arguments seen, and a `hang*` flag that parks the call until it is cancelled,
 * simulating a request that is still in flight.
 */
class FakeSpoonacularRepository : SpoonacularRepository {

    var autocompleteResult: Result<List<IngredientAutocomplete>> = Result.success(emptyList())

    /**
     * Per-query autocomplete answers, consulted before [autocompleteResult].
     *
     * Receipt scanning issues one lookup per receipt line plus fallbacks, so its tests
     * need different answers for different queries rather than one blanket result.
     * Leave it null and [autocompleteResult] behaves exactly as before.
     */
    var autocompleteHandler: ((String) -> Result<List<IngredientAutocomplete>>)? = null
    var recipesResult: Result<List<RecipeByIngredient>> = Result.success(emptyList())
    var detailsResult: Result<List<RecipeInformation>> = Result.success(emptyList())
    var substitutesResult: Result<IngredientSubstitutes> = Result.success(IngredientSubstitutes())
    var similarResult: Result<List<SimilarRecipe>> = Result.success(emptyList())

    var hangAutocomplete = false
    var hangRecipes = false
    var hangDetails = false
    var hangSubstitutes = false
    var hangSimilar = false

    var autocompleteCalls = 0
        private set
    var recipesCalls = 0
        private set
    var detailsCalls = 0
        private set
    var substitutesCalls = 0
        private set
    var similarCalls = 0
        private set

    var lastAutocompleteQuery: String? = null
        private set

    /** Every autocomplete query seen, in order — receipt scanning issues many per scan. */
    val autocompleteQueries = mutableListOf<String>()
    var lastRecipeQuery: List<String>? = null
        private set
    var lastRecipeFilters: Map<String, String>? = null
        private set
    var lastDetailsIds: List<Int>? = null
        private set
    var lastSubstitutesName: String? = null
        private set
    var lastSimilarId: Int? = null
        private set

    override suspend fun autocompleteIngredients(query: String): Result<List<IngredientAutocomplete>> {
        autocompleteCalls++
        lastAutocompleteQuery = query
        autocompleteQueries += query
        if (hangAutocomplete) awaitCancellation()
        return autocompleteHandler?.invoke(query) ?: autocompleteResult
    }

    override suspend fun searchRecipes(
        names: List<String>,
        filters: Map<String, String>,
        number: Int
    ): Result<List<RecipeByIngredient>> {
        recipesCalls++
        lastRecipeQuery = names
        lastRecipeFilters = filters
        if (hangRecipes) awaitCancellation()
        return recipesResult
    }

    override suspend fun getRecipeInformationBulk(ids: List<Int>): Result<List<RecipeInformation>> {
        detailsCalls++
        lastDetailsIds = ids
        if (hangDetails) awaitCancellation()
        return detailsResult
    }

    override suspend fun getIngredientSubstitutes(name: String): Result<IngredientSubstitutes> {
        substitutesCalls++
        lastSubstitutesName = name
        if (hangSubstitutes) awaitCancellation()
        return substitutesResult
    }

    override suspend fun getSimilarRecipes(id: Int, number: Int): Result<List<SimilarRecipe>> {
        similarCalls++
        lastSimilarId = id
        if (hangSimilar) awaitCancellation()
        return similarResult
    }
}

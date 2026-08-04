package com.example.pantryparty.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface SpoonacularApi {

    @GET("food/ingredients/autocomplete")
    suspend fun autocompleteIngredients(
        @Query("query") query: String,
        @Query("number") number: Int = 8,
        @Query("metaInformation") metaInformation: Boolean = true,
        @Query("apiKey") apiKey: String
    ): List<IngredientAutocomplete>

    // What you could use instead of an ingredient you don't have.
    //
    // Looked up by name, not by id: a recipe's extendedIngredient id is not reliably
    // the id autocomplete gives the same ingredient (see PantryIndex in
    // RecipeMatcher.kt), but the canonical `name` agrees across endpoints. Retrofit
    // URL-encodes the value, so "olive oil" is fine.
    @GET("food/ingredients/substitutes")
    suspend fun getIngredientSubstitutes(
        @Query("ingredientName") ingredientName: String,
        @Query("apiKey") apiKey: String
    ): IngredientSubstitutes

    // Filtered recipe search. `filters` carries the user's criteria as query
    // params (see RecipeFilters.toQueryMap); with fillIngredients=true each
    // result reports its used/missed split against includeIngredients.
    @GET("recipes/complexSearch")
    suspend fun searchRecipes(
        @QueryMap filters: Map<String, String>,
        @Query("includeIngredients") includeIngredients: String?,  // comma-separated; null = no ingredient matching
        @Query("fillIngredients") fillIngredients: Boolean = true,
        @Query("ignorePantry") ignorePantry: Boolean = true,       // staples are RecipeMatcher's job
        @Query("number") number: Int = 20,
        @Query("apiKey") apiKey: String
    ): ComplexSearchResponse

    // Full details (with required amounts) for several recipes at once.
    @GET("recipes/informationBulk")
    suspend fun getRecipeInformationBulk(
        @Query("ids") ids: String,                   // comma-separated recipe ids
        // On globally rather than per-call. It costs +0.025 points per recipe, a
        // rounding error next to the 1 point this call already costs, and this
        // endpoint is the app's only source of details: a per-call flag would split
        // RecipeViewModel.detailsById into two flavours of cached RecipeInformation
        // and force a refetch when the detail page opened, which is exactly the
        // property that made the detail page free.
        @Query("includeNutrition") includeNutrition: Boolean = true,
        @Query("apiKey") apiKey: String
    ): List<RecipeInformation>

    // "More like this" for an open recipe. Returns no ingredient data, so the
    // results can't be matched against the pantry without opening one.
    @GET("recipes/{id}/similar")
    suspend fun getSimilarRecipes(
        @Path("id") id: Int,
        @Query("number") number: Int = 4,
        @Query("apiKey") apiKey: String
    ): List<SimilarRecipe>
}

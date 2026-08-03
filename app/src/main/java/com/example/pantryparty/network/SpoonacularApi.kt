package com.example.pantryparty.network

import retrofit2.http.GET
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
        @Query("includeNutrition") includeNutrition: Boolean = false,
        @Query("apiKey") apiKey: String
    ): List<RecipeInformation>
}

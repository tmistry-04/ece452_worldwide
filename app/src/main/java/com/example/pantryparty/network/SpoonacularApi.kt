package com.example.pantryparty.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SpoonacularApi {

    @GET("food/ingredients/autocomplete")
    suspend fun autocompleteIngredients(
        @Query("query") query: String,
        @Query("number") number: Int = 8,
        @Query("metaInformation") metaInformation: Boolean = true,
        @Query("apiKey") apiKey: String
    ): List<IngredientAutocomplete>

    @GET("food/ingredients/search")
    suspend fun searchIngredients(
        @Query("query") query: String,
        @Query("number") number: Int = 10,
        @Query("apiKey") apiKey: String
    ): IngredientSearchResponse

    @GET("food/ingredients/{id}/information")
    suspend fun getIngredientInfo(
        @Path("id") id: Int,
        @Query("amount") amount: Double = 100.0,
        @Query("unit") unit: String = "grams",
        @Query("apiKey") apiKey: String
    ): IngredientInfo

    // Recipes the user can (almost) make from a set of ingredient names.
    @GET("recipes/findByIngredients")
    suspend fun findRecipesByIngredients(
        @Query("ingredients") ingredients: String,   // comma-separated names
        @Query("number") number: Int = 20,
        @Query("ranking") ranking: Int = 2,          // 2 = minimize missing ingredients
        @Query("ignorePantry") ignorePantry: Boolean = true,
        @Query("apiKey") apiKey: String
    ): List<RecipeByIngredient>

    // Full details (with required amounts) for several recipes at once.
    @GET("recipes/informationBulk")
    suspend fun getRecipeInformationBulk(
        @Query("ids") ids: String,                   // comma-separated recipe ids
        @Query("includeNutrition") includeNutrition: Boolean = false,
        @Query("apiKey") apiKey: String
    ): List<RecipeInformation>
}

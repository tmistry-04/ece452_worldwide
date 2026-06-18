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
}

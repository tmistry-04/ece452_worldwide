package com.example.pantryparty.network

import com.example.pantryparty.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object SpoonacularRepository {

    private val api: SpoonacularApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.spoonacular.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpoonacularApi::class.java)
    }

    private val apiKey = BuildConfig.SPOONACULAR_API_KEY

    suspend fun autocompleteIngredients(query: String): Result<List<IngredientAutocomplete>> =
        runCatching { api.autocompleteIngredients(query = query, apiKey = apiKey) }

    suspend fun searchIngredients(query: String): Result<IngredientSearchResponse> =
        runCatching { api.searchIngredients(query = query, apiKey = apiKey) }

    suspend fun getIngredientInfo(id: Int): Result<IngredientInfo> =
        runCatching { api.getIngredientInfo(id = id, apiKey = apiKey) }
}

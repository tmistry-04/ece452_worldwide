package com.example.pantryparty.network

import com.example.pantryparty.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

/**
 * Abstraction over the Spoonacular API so ViewModels can be unit-tested against
 * a fake. Production code uses [SpoonacularRepositoryImpl].
 */
interface SpoonacularRepository {
    suspend fun autocompleteIngredients(query: String): Result<List<IngredientAutocomplete>>
    suspend fun searchRecipes(names: List<String>, filters: Map<String, String>, number: Int): Result<List<RecipeByIngredient>>
    suspend fun getRecipeInformationBulk(ids: List<Int>): Result<List<RecipeInformation>>

    /**
     * Returns the raw response rather than the substitute list: "none found" is a
     * successful call carrying the API's own explanatory message, which is the text
     * worth showing. Unwrapping here would throw it away.
     */
    suspend fun getIngredientSubstitutes(name: String): Result<IngredientSubstitutes>

    suspend fun getSimilarRecipes(id: Int, number: Int): Result<List<SimilarRecipe>>
}

/**
 * Parser settings shared with tests: tolerate response fields we don't model,
 * and coerce explicit nulls to the models' default values.
 */
internal val SpoonacularJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

object SpoonacularRepositoryImpl : SpoonacularRepository {

    private val api: SpoonacularApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // BODY logs full request URLs — including the apiKey query param —
            // so keep logging out of release builds entirely.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.spoonacular.com/")
            .client(client)
            .addConverterFactory(SpoonacularJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SpoonacularApi::class.java)
    }

    private val apiKey = BuildConfig.SPOONACULAR_API_KEY

    override suspend fun autocompleteIngredients(query: String): Result<List<IngredientAutocomplete>> =
        runCatchingApi { api.autocompleteIngredients(query = query, apiKey = apiKey) }

    // Joins ingredient names into the comma-separated form the endpoint expects.
    override suspend fun searchRecipes(
        names: List<String>,
        filters: Map<String, String>,
        number: Int
    ): Result<List<RecipeByIngredient>> =
        runCatchingApi {
            api.searchRecipes(
                filters = filters,
                includeIngredients = names.takeIf { it.isNotEmpty() }?.joinToString(","),
                number = number,
                apiKey = apiKey
            ).results
        }

    override suspend fun getRecipeInformationBulk(ids: List<Int>): Result<List<RecipeInformation>> =
        runCatchingApi {
            api.getRecipeInformationBulk(ids = ids.joinToString(","), apiKey = apiKey)
        }

    override suspend fun getIngredientSubstitutes(name: String): Result<IngredientSubstitutes> =
        runCatchingApi { api.getIngredientSubstitutes(ingredientName = name, apiKey = apiKey) }

    override suspend fun getSimilarRecipes(id: Int, number: Int): Result<List<SimilarRecipe>> =
        runCatchingApi { api.getSimilarRecipes(id = id, number = number, apiKey = apiKey) }

    /**
     * Like [runCatching], but rethrows [CancellationException] so a cancelled
     * caller (e.g. a debounced keystroke) dies quietly instead of surfacing a
     * "job was cancelled" failure to the UI.
     */
    private inline fun <T> runCatchingApi(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

/** Maps API failures to user-readable text; auth, quota, and network errors get clear hints. */
fun friendlyApiError(t: Throwable): String = when {
    t is HttpException && t.code() == 401 ->
        "Spoonacular rejected the API key — set SPOONACULAR_API_KEY in local.properties."
    t is HttpException && t.code() == 402 ->
        "Daily Spoonacular quota reached — try again after the daily reset or add a new API key."
    t is IOException ->
        "Couldn't reach Spoonacular — check your internet connection and try again."
    else -> "Error: ${t.message}"
}

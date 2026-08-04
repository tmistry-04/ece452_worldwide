package com.example.pantryparty.network

import com.example.pantryparty.BuildConfig
import java.io.IOException
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Abstraction over the OpenRouter LLM gateway so ViewModels can be unit-tested
 * against a fake (same convention as [SpoonacularRepository]).
 *
 * OpenRouter is optional: without an `OPENROUTER_API_KEY` in local.properties,
 * [isConfigured] is false and callers skip it — every feature built on it must
 * have a non-LLM fallback.
 */
interface OpenRouterRepository {
    val isConfigured: Boolean

    /** One chat completion; success is the assistant message's raw text. */
    suspend fun complete(system: String, user: String): Result<String>
}

object OpenRouterRepositoryImpl : OpenRouterRepository {

    private val api: OpenRouterApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // BODY logs headers too — including Authorization — so keep logging
            // out of release builds entirely (same posture as Spoonacular).
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        Retrofit.Builder()
            .baseUrl("https://openrouter.ai/")
            .client(client)
            .addConverterFactory(SpoonacularJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenRouterApi::class.java)
    }

    private val apiKey = BuildConfig.OPENROUTER_API_KEY

    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    override suspend fun complete(system: String, user: String): Result<String> = try {
        val response = api.chatCompletions(
            auth = "Bearer $apiKey",
            body = ChatRequest(
                model = BuildConfig.OPENROUTER_MODEL,
                messages = listOf(
                    ChatMessage(role = "system", content = system),
                    ChatMessage(role = "user", content = user)
                )
            )
        )
        val content = response.choices.firstOrNull()?.message?.content
        if (content.isNullOrBlank()) Result.failure(IllegalStateException("Empty completion"))
        else Result.success(content)
    } catch (e: CancellationException) {
        throw e   // a cancelled caller (e.g. a timeout) must die quietly
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

/** Maps OpenRouter failures to user-readable text; auth, credit, and network errors get clear hints. */
fun friendlyLlmError(t: Throwable): String = when {
    t is HttpException && t.code() == 401 ->
        "OpenRouter rejected the API key — check OPENROUTER_API_KEY in local.properties."
    t is HttpException && (t.code() == 402 || t.code() == 429) ->
        "OpenRouter is out of credits or rate-limiting — try again in a moment."
    t is IOException ->
        "No internet connection — receipt scanning needs to reach OpenRouter."
    else -> "Couldn't read the receipt — try again."
}

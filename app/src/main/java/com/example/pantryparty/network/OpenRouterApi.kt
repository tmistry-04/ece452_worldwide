package com.example.pantryparty.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterApi {

    @POST("api/v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") auth: String,          // "Bearer <key>"
        @Body body: ChatRequest,
        @Header("X-Title") appTitle: String = "PantryParty"   // OpenRouter attribution (optional)
    ): ChatResponse
}

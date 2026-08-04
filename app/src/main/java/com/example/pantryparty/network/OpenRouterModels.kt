package com.example.pantryparty.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// OpenRouter speaks the OpenAI chat-completions dialect; only the fields the app
// actually uses are modelled (SpoonacularJson ignores the rest of the response).

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    // Extraction wants determinism, not creativity.
    val temperature: Double = 0.0,
    @SerialName("max_tokens") val maxTokens: Int = 4096
)

@Serializable
data class ChatChoice(val message: ChatMessage? = null)

@Serializable
data class ChatResponse(val choices: List<ChatChoice> = emptyList())

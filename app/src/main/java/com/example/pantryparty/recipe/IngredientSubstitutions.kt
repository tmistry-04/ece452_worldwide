package com.example.pantryparty.recipe

import com.example.pantryparty.network.IngredientSubstitutes

/** What the substitutes endpoint had to say about one ingredient. */
sealed interface SubstituteResult {
    data class Found(val options: List<String>) : SubstituteResult

    /** The API answered and knows nothing — its own wording. Not an error. */
    data class NotFound(val message: String) : SubstituteResult
}

object IngredientSubstitutions {

    /**
     * Reads a substitutes response.
     *
     * Branches on `status`, never on the HTTP code: this endpoint answers "I don't
     * know any" with HTTP 200 and `status: "failure"`, so a call that got here at
     * all may still be a miss. A `success` carrying an empty list is treated the
     * same way — there is nothing to show either way.
     */
    fun read(response: IngredientSubstitutes, requested: String): SubstituteResult {
        val options = response.substitutes.map { it.trim() }.filter { it.isNotEmpty() }
        if (!response.status.equals("success", ignoreCase = true) || options.isEmpty()) {
            return SubstituteResult.NotFound(
                response.message.trim().ifEmpty { "No substitutes known for $requested." }
            )
        }
        return SubstituteResult.Found(options)
    }
}

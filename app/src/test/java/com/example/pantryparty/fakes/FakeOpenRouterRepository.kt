package com.example.pantryparty.fakes

import com.example.pantryparty.network.OpenRouterRepository
import kotlinx.coroutines.awaitCancellation

/**
 * Scriptable OpenRouterRepository, instrumented like [FakeSpoonacularRepository]:
 * tests set the completion they want returned, and can park the call with [hang]
 * to simulate a slow model. Defaults to unconfigured, which is the no-key
 * production posture — the LLM path is skipped entirely.
 */
class FakeOpenRouterRepository : OpenRouterRepository {

    override var isConfigured: Boolean = false

    var completion: Result<String> = Result.failure(IllegalStateException("not scripted"))

    var hang = false

    var completeCalls = 0
        private set
    var lastSystem: String? = null
        private set
    var lastUser: String? = null
        private set

    override suspend fun complete(system: String, user: String): Result<String> {
        completeCalls++
        lastSystem = system
        lastUser = user
        if (hang) awaitCancellation()
        return completion
    }
}

package com.example.pantryparty

import com.example.pantryparty.network.friendlyApiError
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Guards the error-to-user-text mapping, hint by hint. */
class FriendlyApiErrorTest {

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun badKey_pointsAtLocalProperties() =
        assertTrue(friendlyApiError(http(401)).contains("SPOONACULAR_API_KEY"))

    @Test
    fun quota_mentionsTheDailyLimit() =
        assertTrue(friendlyApiError(http(402)).contains("quota"))

    @Test
    fun networkFailure_suggestsCheckingTheConnection() =
        assertTrue(friendlyApiError(IOException("timeout")).contains("connection"))

    @Test
    fun anythingElse_fallsBackToTheExceptionMessage() =
        assertTrue(friendlyApiError(IllegalStateException("boom")).contains("boom"))
}

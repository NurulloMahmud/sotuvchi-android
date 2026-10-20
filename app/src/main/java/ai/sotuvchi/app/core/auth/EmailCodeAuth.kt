package ai.sotuvchi.app.core.auth

import ai.sotuvchi.app.core.format.Countdown
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.network.ApiClient
import ai.sotuvchi.app.core.network.ApiError
import ai.sotuvchi.app.core.network.ApiJson
import ai.sotuvchi.app.core.network.ApiRequest
import ai.sotuvchi.app.core.network.Envelope
import ai.sotuvchi.app.core.network.InstantSerializer
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import okhttp3.Request

/**
 * Sign-in with a 6-digit code sent by email. The same flow signs in existing
 * owners and registers new ones: a new email gets its account and workspace
 * when the code is verified.
 *
 * 1. [requestCode] asks the server to email a code (valid 10 minutes, one per
 *    60 seconds, at most 5 per hour).
 * 2. [verify] exchanges the code for a session token, which the app keeps in
 *    encrypted storage and sends as `Authorization: Bearer <token>`.
 * 3. [logout] deletes the session on the server.
 */
class EmailCodeAuth(private val api: ApiClient) {

    sealed class Failure : Exception() {
        data object InvalidEmail : Failure()
        data object NotAllowed : Failure()

        /** Asked again too soon, or too many codes this hour. */
        data class TooSoon(val retryAfterSeconds: Int) : Failure()
        data object SendFailed : Failure()
        data object InvalidCode : Failure()
        data object WrongCode : Failure()
        data object CodeExpired : Failure()

        /** Five wrong guesses: a new code is needed. */
        data object TooManyAttempts : Failure()

        val userMessage: String
            get() = when (this) {
                InvalidEmail -> L("Enter a valid email address.")
                NotAllowed -> L("This email address is not allowed to sign in.")
                is TooSoon -> L("Wait {time} before asking for another code.", mapOf("time" to Countdown.clock(retryAfterSeconds)))
                SendFailed -> L("We could not send the code. Please try again in a moment.")
                InvalidCode -> L("Enter the 6 digits from the email.")
                WrongCode -> L("Wrong code. Check the email and try again.")
                CodeExpired -> L("This code has expired. Send a new one.")
                TooManyAttempts -> L("Too many wrong codes. Send a new code.")
            }

        /** The current code can no longer be used. */
        val needsNewCode: Boolean get() = this == CodeExpired || this == TooManyAttempts
    }

    @Serializable
    data class CodeRequest(val expiresInSeconds: Int, val resendAfterSeconds: Int)

    @Serializable
    data class SignedInUser(val id: String, val email: String? = null)

    @Serializable
    data class SignedIn(
        val token: String,
        @Serializable(with = InstantSerializer::class) val expiresAt: Instant? = null,
        val isNewUser: Boolean = false,
        val user: SignedInUser? = null,
    )

    @Serializable
    private data class EmailBody(val email: String)

    @Serializable
    private data class VerifyBody(val email: String, val code: String)

    @Serializable
    private data class FailureBody(val error: String? = null, val retryAfterSeconds: Int? = null)

    suspend fun requestCode(rawEmail: String): CodeRequest {
        val email = normalize(rawEmail)
        if (!isValidEmail(email)) throw Failure.InvalidEmail
        val response = api.exchange(ApiRequest.post("/mobile/auth/request-code", EmailBody.serializer(), EmailBody(email)))
        if (response.status != 200) throw requestFailure(response.status, response.body, response.retryAfter)
        return decodeData(CodeRequest.serializer(), response.body)
    }

    suspend fun verify(rawEmail: String, rawCode: String): SignedIn {
        val email = normalize(rawEmail)
        val code = sanitize(rawCode)
        if (code.length != CODE_LENGTH) throw Failure.InvalidCode
        val response = api.exchange(ApiRequest.post("/mobile/auth/verify-code", VerifyBody.serializer(), VerifyBody(email, code)))
        if (response.status != 200) throw verifyFailure(response.status, response.body)
        return decodeData(SignedIn.serializer(), response.body)
    }

    /**
     * Ends the session on the server. Always succeeds there; the app forgets
     * the token whatever happens here.
     */
    suspend fun logout(token: String) {
        try {
            val request = api.buildRequest(ApiRequest.post("/mobile/auth/logout"))
                .newBuilder().header("Authorization", "Bearer $token").build()
            val call = api.http.newCall(request)
            call.timeout().timeout(10, TimeUnit.SECONDS)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { call.execute().close() }
            }
        } catch (_: Exception) {
        }
    }

    private fun <T> decodeData(serializer: KSerializer<T>, body: String): T {
        try {
            return ApiJson.json.decodeFromString(Envelope.serializer(serializer), body).data
                ?: throw ApiError.Decoding("Missing data")
        } catch (error: ApiError) {
            throw error
        } catch (error: Exception) {
            throw ApiError.Decoding(error.toString())
        }
    }

    companion object {
        const val CODE_LENGTH = 6

        fun normalize(email: String): String = email.trim().lowercase()

        private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        fun isValidEmail(email: String): Boolean = emailPattern.matches(email)

        /** Digits only, at most six. Codes can start with 0, so they stay strings. */
        fun sanitize(code: String): String = code.filter { it in '0'..'9' }.take(CODE_LENGTH)

        /** "0:42" or "12:05". */
        fun countdown(seconds: Int): String = Countdown.clock(seconds)

        fun requestFailure(status: Int, body: String, retryAfterHeader: String? = null): Exception = when (status) {
            400 -> Failure.InvalidEmail
            403 -> Failure.NotAllowed
            429 -> {
                val fromBody = runCatching { ApiJson.json.decodeFromString(FailureBody.serializer(), body).retryAfterSeconds }.getOrNull()
                Failure.TooSoon(fromBody ?: retryAfterHeader?.toIntOrNull() ?: 60)
            }
            502 -> Failure.SendFailed
            else -> ApiError.from(status, null)
        }

        fun verifyFailure(status: Int, body: String): Exception = when (status) {
            400 -> Failure.InvalidCode
            401 -> {
                val message = runCatching { ApiJson.json.decodeFromString(FailureBody.serializer(), body).error }.getOrNull() ?: ""
                if (message.lowercase().contains("expired")) Failure.CodeExpired else Failure.WrongCode
            }
            429 -> Failure.TooManyAttempts
            else -> ApiError.from(status, null)
        }
    }
}

package ai.sotuvchi.app

import ai.sotuvchi.app.core.auth.EmailCodeAuth
import ai.sotuvchi.app.core.auth.EmailCodeAuth.Failure
import ai.sotuvchi.app.core.network.ApiClient
import ai.sotuvchi.app.core.network.ApiError
import ai.sotuvchi.app.core.network.ApiJson
import ai.sotuvchi.app.core.network.ApiRequest
import ai.sotuvchi.app.core.network.Envelope
import ai.sotuvchi.app.core.model.CampaignActiveBody
import okio.Buffer
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

private fun Request.bodyText(): String {
    val buffer = Buffer()
    body?.writeTo(buffer)
    return buffer.readUtf8()
}

class EmailCodeAuthTest {
    @Test
    fun keepsOnlySixDigitsAndLeadingZeros() {
        assertEquals("012345", EmailCodeAuth.sanitize("012345"))
        assertEquals("012345", EmailCodeAuth.sanitize(" 01-23 45 "))
        assertEquals("123456", EmailCodeAuth.sanitize("1234567"))
        assertEquals("123", EmailCodeAuth.sanitize("12a3"))
        assertEquals("", EmailCodeAuth.sanitize("١٢٣"))
    }

    @Test
    fun normalizesAndValidatesEmails() {
        assertEquals("owner@shop.uz", EmailCodeAuth.normalize("  Owner@Shop.UZ "))
        assertTrue(EmailCodeAuth.isValidEmail("owner@shop.uz"))
        assertFalse(EmailCodeAuth.isValidEmail("owner@shop"))
        assertFalse(EmailCodeAuth.isValidEmail("owner shop@x.uz"))
    }

    @Test
    fun formatsCountdowns() {
        assertEquals("0:42", EmailCodeAuth.countdown(42))
        assertEquals("10:05", EmailCodeAuth.countdown(605))
        assertEquals("0:00", EmailCodeAuth.countdown(-3))
    }

    @Test
    fun mapsRequestCodeErrors() {
        assertEquals(Failure.InvalidEmail, EmailCodeAuth.requestFailure(400, """{"error":"Invalid email"}"""))
        assertEquals(Failure.NotAllowed, EmailCodeAuth.requestFailure(403, ""))
        assertEquals(Failure.SendFailed, EmailCodeAuth.requestFailure(502, ""))
        assertEquals(
            Failure.TooSoon(37),
            EmailCodeAuth.requestFailure(429, """{"success":false,"error":"Wait","retryAfterSeconds":37}"""),
        )
        assertEquals(Failure.TooSoon(120), EmailCodeAuth.requestFailure(429, "", "120"))
        assertEquals(Failure.TooSoon(60), EmailCodeAuth.requestFailure(429, ""))
    }

    @Test
    fun mapsVerifyCodeErrors() {
        assertEquals(Failure.InvalidCode, EmailCodeAuth.verifyFailure(400, ""))
        assertEquals(Failure.WrongCode, EmailCodeAuth.verifyFailure(401, """{"error":"Wrong code"}"""))
        assertEquals(Failure.CodeExpired, EmailCodeAuth.verifyFailure(401, """{"error":"Code expired, request a new one"}"""))
        assertEquals(Failure.TooManyAttempts, EmailCodeAuth.verifyFailure(429, ""))
        assertTrue(Failure.CodeExpired.needsNewCode)
        assertTrue(Failure.TooManyAttempts.needsNewCode)
        assertFalse(Failure.WrongCode.needsNewCode)
        assertEquals(ApiError.Server(500, null), EmailCodeAuth.verifyFailure(500, ""))
    }

    @Test
    fun decodesTheSignedInPayload() {
        val payload = ApiJson.json.decodeFromString(
            Envelope.serializer(EmailCodeAuth.SignedIn.serializer()),
            """
            { "success": true, "data": { "token": "9f2c0000000000000000000000000000000000000000000000000000000000ab",
              "expiresAt": "2026-11-16T10:00:00.000Z", "isNewUser": true, "user": { "id": "clx1", "email": "owner@example.com" } } }
            """,
        )
        val signedIn = payload.data!!
        assertEquals(64, signedIn.token.length)
        assertTrue(signedIn.isNewUser)
        assertEquals("owner@example.com", signedIn.user?.email)
        assertNotNull(signedIn.expiresAt)

        val request = ApiJson.json.decodeFromString(
            Envelope.serializer(EmailCodeAuth.CodeRequest.serializer()),
            """{ "success": true, "data": { "expiresInSeconds": 600, "resendAfterSeconds": 60 } }""",
        )
        assertEquals(EmailCodeAuth.CodeRequest(600, 60), request.data)
    }
}

class ApiClientTest {
    @Test
    fun sendsTheSessionAsABearerToken() {
        val client = ApiClient("https://sotuvchi.ai/api", "abc123")
        val request = client.buildAuthorizedRequest(ApiRequest.get("/orders"))
        assertEquals("Bearer abc123", request.header("Authorization"))
        assertNull(request.header("Cookie"))

        val signedOut = ApiClient("https://sotuvchi.ai/api")
        val anonymous = signedOut.buildAuthorizedRequest(
            ApiRequest.postJson("/mobile/auth/request-code", """{"email":"a@b.uz"}"""),
        )
        assertNull(anonymous.header("Authorization"))
        assertEquals("https://sotuvchi.ai/api/mobile/auth/request-code", anonymous.url.toString())
    }

    @Test
    fun cookieNameFollowsTheScheme() {
        assertEquals("__Secure-authjs.session-token", ApiClient("https://sotuvchi.ai/api").sessionCookieName)
        assertEquals("authjs.session-token", ApiClient("http://localhost:3000/api").sessionCookieName)
    }

    @Test
    fun productionIsTheDefaultAndTheSiteIsDerived() {
        // The shipped setting, independent of any debug override.
        assertEquals("https://sotuvchi.ai/api", ApiClient.PRODUCTION_API_URL)
        val client = ApiClient("https://sotuvchi.ai/api")
        assertEquals("https://sotuvchi.ai/", client.siteUrl.toString())
        assertEquals("https://sotuvchi.ai/login", client.webPage("/login").toString())
        val local = ApiClient("http://localhost:8787/api/v1")
        assertEquals("http://localhost:8787/", local.siteUrl.toString())
    }

    @Test
    fun buildsRequestsWithQueryAndJson() {
        val client = ApiClient("https://sotuvchi.ai/api")
        val request = client.buildRequest(
            ApiRequest.patch("/automations", CampaignActiveBody.serializer(), CampaignActiveBody(false), listOf("id" to "a1")),
        )
        assertEquals("https://sotuvchi.ai/api/automations?id=a1", request.url.toString())
        assertEquals("PATCH", request.method)
        assertEquals("application/json", request.body?.contentType()?.toString())
        assertEquals("""{"isActive":false}""", request.bodyText())
    }

    @Test
    fun resolvesRelativeImageUrlsAndKnowsItsHost() {
        val client = ApiClient("https://sotuvchi.ai/api")
        val proxied = client.resolve("/api/stores/s1/external-image?src=a")!!
        assertEquals("https://sotuvchi.ai/api/stores/s1/external-image?src=a", proxied.toString())
        assertTrue(client.isOwnHost(proxied))
        assertFalse(client.isOwnHost(client.resolve("https://bucket.s3.amazonaws.com/x.jpg")!!))
    }

    @Test
    fun mapsStatusCodes() {
        assertEquals(ApiError.Unauthorized, ApiError.from(401, null))
        assertEquals(ApiError.Conflict("x"), ApiError.from(409, "x"))
        assertEquals(ApiError.Upstream("window"), ApiError.from(502, "window"))
        assertEquals(ApiError.TooLarge, ApiError.from(413, null))
        assertEquals(ApiError.Server(500, null), ApiError.from(500, null))
    }
}

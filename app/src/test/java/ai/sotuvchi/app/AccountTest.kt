package ai.sotuvchi.app

import ai.sotuvchi.app.TestSupport.decode
import ai.sotuvchi.app.app.AppState
import ai.sotuvchi.app.core.model.AccountDeletionResult
import ai.sotuvchi.app.core.model.DashboardStats
import ai.sotuvchi.app.core.model.PlanStatus
import ai.sotuvchi.app.core.network.ApiClient
import ai.sotuvchi.app.core.network.deleteAccountRequest
import ai.sotuvchi.app.core.network.signedInWebUrl
import ai.sotuvchi.app.core.network.webHandoffRequest
import ai.sotuvchi.app.core.network.Envelope
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AccountDeletionTest {
    @Test
    fun sendsAConfirmedDelete() {
        val client = ApiClient("https://sotuvchi.ai/api", "abc123")
        val spec = ApiClient.deleteAccountRequest()
        val request = client.buildAuthorizedRequest(spec)
        assertEquals("https://sotuvchi.ai/api/account", request.url.toString())
        assertEquals("DELETE", request.method)
        assertEquals("Bearer abc123", request.header("Authorization"))
        assertEquals("application/json", request.body?.contentType()?.toString())
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        assertEquals("""{"confirm":true}""", buffer.readUtf8())
        assertEquals(60L, spec.timeoutSeconds)
    }

    @Test
    fun decodesTheResult() {
        val result = decode<Envelope<AccountDeletionResult>>(
            """{"success":true,"data":{"deletedWorkspaces":1,"transferredWorkspaces":0,"disconnectedInstagramAccounts":2}}""",
        ).data!!
        assertEquals(1, result.deletedWorkspaces)
        assertEquals(0, result.transferredWorkspaces)
        assertEquals(2, result.disconnectedInstagramAccounts)
    }
}

class PlanStatusTest {
    private val now = Instant.ofEpochSecond(1_800_000_000)

    @Test
    fun readsTheExpiryDate() {
        val later = now.plus(Duration.ofDays(30))
        val earlier = now.minus(Duration.ofDays(1))
        assertEquals(PlanStatus.None, PlanStatus.of(null, now))
        assertEquals(PlanStatus.Active(later), PlanStatus.of(later, now))
        assertEquals(PlanStatus.Ended(earlier), PlanStatus.of(earlier, now))
        assertEquals(PlanStatus.Ended(now), PlanStatus.of(now, now))
        assertTrue(PlanStatus.of(later, now).isActive)
        assertFalse(PlanStatus.of(earlier, now).isActive)
    }

    @Test
    fun warnsInTheLastWeek() {
        assertTrue(PlanStatus.of(now.plus(Duration.ofDays(3)), now).endsSoon(now))
        assertFalse(PlanStatus.of(now.plus(Duration.ofDays(10)), now).endsSoon(now))
        assertFalse(PlanStatus.of(null, now).endsSoon(now))
    }

    @Test
    fun comesFromTheDashboard() {
        val stats = decode<DashboardStats>(
            """{"workspace":{"name":"Zebo Shoes","dmsSentThisPeriod":4,"planExpiresAt":"2030-01-01T00:00:00.000Z"},"billingEnabled":true}""",
        )
        assertEquals(true, stats.billingEnabled)
        assertEquals("Zebo Shoes", stats.workspace?.name)
        val state = AppState(billingEnabled = true, planExpiresAt = stats.workspace?.planExpiresAt)
        assertTrue(state.plan.isActive)
    }
}

class WebHandoffTest {
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun stop() = server.shutdown()

    private fun client(token: String? = "abc123") = ApiClient(server.url("/api").toString().trimEnd('/'), token)

    @Test
    fun asksForALinkWithTheBearerToken() {
        val api = client()
        val spec = ApiClient.webHandoffRequest("/settings")
        val request = api.buildAuthorizedRequest(spec)
        assertTrue(request.url.toString().endsWith("/api/mobile/auth/web-handoff"))
        assertEquals("POST", request.method)
        assertEquals("Bearer abc123", request.header("Authorization"))
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        assertEquals("""{"path":"/settings"}""", buffer.readUtf8())
        assertEquals(8L, spec.timeoutSeconds)
    }

    @Test
    fun opensTheOneTimeLink() = runBlocking {
        val api = client()
        val link = server.url("/api/mobile/auth/web?code=${"a".repeat(64)}&next=%2Fsettings").toString()
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"url":"$link","expiresInSeconds":60}}"""))
        val url = api.signedInWebUrl("/settings")
        assertEquals(link, url)
        assertEquals(1, server.requestCount)
        assertEquals("Bearer abc123", server.takeRequest().getHeader("Authorization"))
        assertFalse("The session token never goes into a URL", url.contains("abc123"))
    }

    @Test
    fun fallsBackToThePlainPage() = runBlocking {
        val plain = server.url("/stores").toString()

        // The server cannot make a link.
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"success":false,"error":"boom"}"""))
        assertEquals(plain, client().signedInWebUrl("/stores"))

        // The answer is not what was expected.
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"nope":1}}"""))
        assertEquals(plain, client().signedInWebUrl("/stores"))

        // The link points at another site: it is not followed.
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"url":"https://evil.example/steal","expiresInSeconds":60}}"""))
        assertEquals(plain, client().signedInWebUrl("/stores"))

        // Signed out: no call at all.
        val before = server.requestCount
        assertEquals(plain, client(token = null).signedInWebUrl("/stores"))
        assertEquals(before, server.requestCount)
    }
}

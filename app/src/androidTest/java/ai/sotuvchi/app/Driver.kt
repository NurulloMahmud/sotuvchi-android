package ai.sotuvchi.app

import ai.sotuvchi.app.app.AppConfig
import ai.sotuvchi.app.core.store.DataStoreKeyValueStore
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking

/**
 * Drives the app against the mock API: launch signed out, sign in with the
 * code the mock sends, move between screens. Shared by the screen tour and the
 * store screenshots.
 */
@OptIn(ExperimentalTestApi::class)
class Driver(private val compose: ComposeTestRule) {
    private var scenario: ActivityScenario<MainActivity>? = null

    /** Wipes the test session (only that one) so the next launch starts signed out. */
    fun wipeSession() = runBlocking {
        val store = DataStoreKeyValueStore(ApplicationProvider.getApplicationContext())
        AppConfig(keyPrefix = "uitest.").let { config ->
            store.remove(config.sessionTokenKey)
            store.remove(config.emailKey)
            store.remove(config.roleKey)
        }
    }

    fun launch(language: String = "en", hideBanner: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("uiTesting", true)
            .putExtra("apiURL", Mock.API_URL)
            .putExtra("language", language)
            .putExtra("hideBanner", hideBanner)
        scenario = ActivityScenario.launch(intent)
    }

    fun close() {
        scenario?.close()
    }

    fun signIn(email: String = "owner@zebo.uz") {
        waitForTag("signIn.email", 20_000)
        compose.onNodeWithTag("signIn.email").performTextReplacement(email)
        compose.onNodeWithTag("signIn.sendCode").performClick()
        val code = Mock.code(email)
        waitForTag("signIn.code")
        compose.onNodeWithTag("signIn.code").performTextReplacement(code)
        waitForTag("tab.more", 20_000)
    }

    fun tab(name: String) {
        compose.onNodeWithTag("tab.$name").performClick()
    }

    fun tap(tag: String, scroll: Boolean = false) {
        waitForTag(tag)
        val node = compose.onNodeWithTag(tag)
        if (scroll) node.performScrollTo()
        node.performClick()
    }

    fun back() = tap("back")

    fun waitForText(text: String, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { exists { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() } }
    }

    fun waitForTag(tag: String, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { exists { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    }

    fun hasText(text: String): Boolean = exists { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    /** Right after a launch there is no compose hierarchy yet; that counts as "not there yet". */
    private fun exists(check: () -> Boolean): Boolean = try {
        check()
    } catch (_: IllegalStateException) {
        false
    }
}

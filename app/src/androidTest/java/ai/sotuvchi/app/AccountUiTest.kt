package ai.sotuvchi.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import ai.sotuvchi.app.app.AppConfig
import ai.sotuvchi.app.core.store.DataStoreKeyValueStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Account flows end to end against the mock API in Tools/mock-server:
 * deleting the account and opening the website signed in.
 * `make ui-test` starts the mock and runs these on the emulator. The app is
 * launched with the uiTesting flag, so its session lives under separate keys
 * and a real sign-in on the same device is left alone.
 */
@OptIn(ExperimentalTestApi::class)
class AccountUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val email = "owner@zebo.uz"
    private var scenario: ActivityScenario<MainActivity>? = null

    /** Every test starts signed out: the test session (and only that one) is wiped from the device. */
    @Before
    fun startSignedOut() = runBlocking {
        val store = DataStoreKeyValueStore(ApplicationProvider.getApplicationContext())
        AppConfig(keyPrefix = "uitest.").let { config ->
            store.remove(config.sessionTokenKey)
            store.remove(config.emailKey)
            store.remove(config.roleKey)
        }
    }

    @After
    fun close() {
        scenario?.close()
    }

    /**
     * The server fails the first time: the user stays signed in and gets a
     * retry. The retry deletes the account and returns to sign-in, and the
     * same email then starts a new, empty account.
     */
    @Test
    fun failedDeleteCanBeRetried() {
        Mock.reset(failDelete = true)
        launch()
        signIn()
        openDeleteAccount()

        waitForText("Your team keeps the workspace. The longest-standing member becomes its owner.")
        confirmDelete()

        waitForText("Retry", timeoutMs = 15_000)
        compose.onNodeWithText("Something went wrong on our side. Try again in a moment.").assertIsDisplayed()
        compose.onNodeWithTag("deleteAccount.delete").assertIsDisplayed() // still signed in, on the delete screen

        Mock.failDelete(false)
        compose.onNodeWithText("Retry").performClick()

        waitForTag("signIn.email", timeoutMs = 15_000) // back on sign-in after the delete
        waitForText("Your account was deleted.") // the deletion is confirmed
        compose.onAllNodesWithText(email).assertCountEquals(0) // the saved email was cleared

        signIn()
        waitForText("Welcome to Sotuvchi", timeoutMs = 15_000) // the same email starts a new account
    }

    /**
     * An owner with no team is told the workspace, stores, orders, chats and
     * the Instagram connection go with the account, before confirming.
     */
    @Test
    fun soloOwnerIsToldTheWorkspaceGoesToo() {
        Mock.reset(soloOwner = true)
        launch()
        signIn()
        openDeleteAccount()

        waitForText("Your workspace is deleted with everything in it: stores, products, orders, receipts, chats and campaigns.")
        compose.onNodeWithText("Your Instagram accounts are disconnected, and Sotuvchi deletes its access to them.").assertIsDisplayed()

        compose.onNodeWithTag("deleteAccount.delete").performClick()
        // The confirmation names what is lost.
        waitForText("Your account, stores, orders, chats and Instagram connection are deleted for good. This cannot be undone.")
    }

    /**
     * "Open the web dashboard" opens the browser with a one-time link, and the
     * browser uses it, so the user is signed in on the web without logging in
     * again.
     */
    @Test
    fun openWebSignsTheBrowserIn() {
        Mock.reset()
        launch()
        signIn()
        compose.onNodeWithTag("tab.more").performClick()
        compose.onNodeWithTag("more.openWeb").performScrollTo().performClick()

        // The app asked the server for a one-time link for the dashboard...
        assertTrue("The app asked for a one-time link", waitUntilHandoffAsked("/dashboard"))
        // ...and the browser opened it.
        assertTrue("The browser opened the one-time sign-in link", Mock.waitForHandoff("/dashboard"))
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    }

    // region Steps

    private fun launch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("uiTesting", true)
            .putExtra("apiURL", Mock.API_URL)
            .putExtra("language", "en")
        scenario = ActivityScenario.launch(intent)
    }

    /** Email, then the code the mock sent, read back from the mock. */
    private fun signIn() {
        waitForTag("signIn.email", timeoutMs = 20_000) // the app starts signed out
        // A previous sign-in may have left the email filled in.
        compose.onNodeWithTag("signIn.email").performTextReplacement(email)
        compose.onNodeWithTag("signIn.sendCode").performClick()

        val code = Mock.code(email)
        waitForTag("signIn.code") // the code step shows
        compose.onNodeWithTag("signIn.code").performTextInput(code)
        waitForTag("tab.more", timeoutMs = 20_000) // signed in
    }

    private fun openDeleteAccount() {
        compose.onNodeWithTag("tab.more").performClick()
        compose.onNodeWithTag("more.deleteAccount").performScrollTo().performClick()
        waitForTag("deleteAccount.delete") // the delete screen opens
    }

    /** Taps delete, then the destructive button in the confirmation dialog. */
    private fun confirmDelete() {
        compose.onNodeWithTag("deleteAccount.delete").performClick()
        waitForTag("dialog.confirm") // the confirmation dialog shows
        compose.onNodeWithTag("dialog.confirm").performClick()
    }

    private fun waitForText(text: String, timeoutMs: Long = 8_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForTag(tag: String, timeoutMs: Long = 8_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitUntilHandoffAsked(path: String): Boolean {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (Mock.handoffs().any { it.first == path }) return true
            Thread.sleep(200)
        }
        return false
    }

    // endregion
}

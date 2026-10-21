package ai.sotuvchi.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Visits every screen against the mock and checks that real content from each
 * response shows up. Run against the shrunk QA build (`make ui-test-qa`), this
 * is what proves R8 did not break JSON decoding or a screen: a missing
 * serializer or a renamed field shows as an error banner or an empty list.
 */
@OptIn(ExperimentalTestApi::class)
class TourUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()
    private val app = Driver(compose)

    @Before
    fun startSignedOut() {
        Mock.reset()
        app.wipeSession()
        app.launch()
        app.signIn()
    }

    @After
    fun close() = app.close()

    @Test
    fun everyScreenShowsItsData() {
        // Home: the dashboard totals and top products decode.
        app.tab("home")
        app.waitForText("Approved sales")
        app.waitForText("Top products")

        // Orders, an order with its receipt.
        app.tab("orders")
        app.tap("order.1048")
        app.waitForTag("order.approve")
        app.waitForTag("receipt.image")
        app.waitForText("Nike Air Max 90")
        app.back()

        // Chats and a thread with its messages.
        app.tab("chats")
        app.tap("chat.c-aziza")
        app.waitForText("Chekingiz qabul qilindi")
        app.waitForTag("chat.composer")
        app.back()

        // Products and the editor.
        app.tab("catalog")
        app.tap("product.Nike Air Max 90")
        app.waitForTag("product.name")
        app.waitForText("Original, leather upper.")
        app.back()

        // More: stores, team, campaigns, Instagram accounts, notifications.
        app.tab("more")
        app.waitForText("Zebo Karimova")
        app.tap("more.stores", scroll = true)
        app.waitForTag("store.Zebo Shoes")
        app.back()
        app.tap("more.team", scroll = true)
        app.waitForText("Bahrom")
        app.back()
        app.tap("more.campaigns", scroll = true)
        app.waitForText("Air Max price DM")
        app.back()
        compose.onNodeWithText("Instagram accounts").performScrollTo().performClick()
        app.waitForText("@zebo.shoes")
        app.back()
        compose.onNodeWithText("Notifications").performScrollTo().performClick()
        app.waitForText("Aziza Karimova")
    }
}

package ai.sotuvchi.app

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Takes the Play Store phone screenshots from the sample data of the mock API.
 * It does nothing in a normal test run; `make screenshots` (Tools/store-screenshots.sh) starts it
 * with `-e screenshotLanguage en|ru|uz` after putting the emulator in demo mode. The banner that
 * names the server is hidden, which the app only allows for a local server.
 */
@OptIn(ExperimentalTestApi::class)
class StoreScreenshots {
    @get:Rule
    val compose = createEmptyComposeRule()
    private val app = Driver(compose)
    private val language: String? = InstrumentationRegistry.getArguments().getString("screenshotLanguage")
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun launch() {
        assumeNotNull(language)
        Mock.reset()
        app.wipeSession()
        app.launch(language = language!!, hideBanner = true)
        app.signIn()
    }

    @After
    fun close() = app.close()

    @Test
    fun phoneScreenshots() {
        // Home, with its chart drawn.
        app.tab("home")
        app.waitForTag("home.chart")
        shot("01-home")

        app.tab("orders")
        app.waitForTag("order.1048")
        shot("02-orders")

        app.tap("order.1048")
        app.waitForTag("receipt.image")
        shot("03-order")

        app.tap("receipt.image")
        app.waitForTag("receiptViewer")
        shot("04-receipt")
        app.tap("receiptViewer.close")
        app.back()

        app.tab("chats")
        app.waitForTag("chat.c-aziza")
        shot("05-chats")

        app.tap("chat.c-aziza")
        app.waitForTag("chat.composer")
        shot("06-chat")
        app.back()

        app.tab("catalog")
        app.waitForTag("product.Nike Air Max 90")
        shot("07-products")

        app.tab("more")
        app.waitForTag("more.openWeb")
        shot("08-more")
    }

    /** Waits for pictures and animations to settle, then saves the whole screen, system bar included. */
    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(1800)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        bitmap.setHasAlpha(false) // Play wants 24-bit PNGs; an opaque bitmap is saved without an alpha channel
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "store-screens/$language").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

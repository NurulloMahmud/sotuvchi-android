package ai.sotuvchi.app

import ai.sotuvchi.app.app.AppConfig
import ai.sotuvchi.app.app.AppContainer
import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.app.RootContent
import ai.sotuvchi.app.core.l10n.AppLanguage
import ai.sotuvchi.app.core.l10n.Localizer
import ai.sotuvchi.app.core.store.DataStoreKeyValueStore
import ai.sotuvchi.app.design.SotuvchiTheme
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels {
        viewModelFactory { initializer { AppModel(AppContainer.create(applicationContext, resolveConfig(intent))) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTestLanguage(intent)
        enableEdgeToEdge()
        setContent {
            SotuvchiTheme {
                RootContent(model)
            }
        }
    }

    /** UI tests launch with a fixed language (not saved), so their texts do not depend on the device. */
    private fun applyTestLanguage(intent: Intent?) {
        if (!BuildConfig.DEBUG_OVERRIDES || intent?.getBooleanExtra("uiTesting", false) != true) return
        AppLanguage.fromCode(intent.getStringExtra("language"))?.let { Localizer.set(it) }
    }

    /**
     * Debug builds can point at another server (for example the mock in
     * Tools/mock-server) with `--es apiURL <url>`; the choice is kept until
     * another one is given, and an empty value clears it. UI tests launch with
     * `--ez uiTesting true`, which keeps their session under separate keys.
     */
    private fun resolveConfig(intent: Intent?): AppConfig {
        if (!BuildConfig.DEBUG_OVERRIDES) return AppConfig()
        val kv = DataStoreKeyValueStore(applicationContext)
        val uiTesting = intent?.getBooleanExtra("uiTesting", false) == true
        val explicit = intent?.getStringExtra("apiURL")
        var apiUrl: String? = explicit
        if (!uiTesting) {
            if (explicit != null) {
                runBlocking { if (explicit.isBlank()) kv.remove("debug.apiURL") else kv.set("debug.apiURL", explicit) }
            } else {
                apiUrl = runBlocking { kv.get("debug.apiURL") }
            }
        }
        val valid = apiUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        return AppConfig(apiUrl = valid ?: BuildConfig.API_URL, keyPrefix = if (uiTesting) "uitest." else "")
    }
}

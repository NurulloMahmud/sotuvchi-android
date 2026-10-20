package ai.sotuvchi.app

import ai.sotuvchi.app.app.LANGUAGE_KEY
import ai.sotuvchi.app.core.l10n.AppLanguage
import ai.sotuvchi.app.core.l10n.Localizer
import ai.sotuvchi.app.core.store.DataStoreKeyValueStore
import android.app.Application
import kotlinx.coroutines.runBlocking

class SotuvchiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val saved = runBlocking { DataStoreKeyValueStore(this@SotuvchiApp).get(LANGUAGE_KEY) }
        Localizer.install(
            loader = { language -> loadTable(language) },
            language = AppLanguage.fromCode(saved) ?: AppLanguage.deviceDefault(),
        )
    }

    private fun loadTable(language: AppLanguage): Map<String, String> = try {
        assets.open("${language.code}.json").bufferedReader().use { Localizer.parseTable(it.readText()) }
    } catch (_: Exception) {
        emptyMap()
    }
}

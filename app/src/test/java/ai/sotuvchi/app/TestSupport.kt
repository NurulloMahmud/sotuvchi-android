package ai.sotuvchi.app

import ai.sotuvchi.app.core.l10n.AppLanguage
import ai.sotuvchi.app.core.l10n.Localizer
import ai.sotuvchi.app.core.network.ApiJson
import java.io.File

object TestSupport {
    /** The app reads translations from assets; unit tests read the same files from the module directory. */
    fun assetFile(name: String) = File("src/main/assets/$name")

    fun table(language: AppLanguage): Map<String, String> =
        Localizer.parseTable(assetFile("${language.code}.json").readText())

    fun useLanguage(language: AppLanguage) {
        Localizer.install(loader = { table(it) }, language = language)
    }

    inline fun <reified T> decode(json: String): T = ApiJson.json.decodeFromString<T>(json)
}

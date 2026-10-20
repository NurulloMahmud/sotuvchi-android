package ai.sotuvchi.app.core.l10n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Interface languages, in picker order. Names are shown in their own language,
 * matching the web app's picker.
 */
enum class AppLanguage(val code: String, val nativeName: String, val locale: Locale) {
    UZ("uz", "O'zbekcha", Locale.forLanguageTag("uz-Latn-UZ")),
    RU("ru", "Русский", Locale.forLanguageTag("ru-RU")),
    EN("en", "English", Locale.forLanguageTag("en-US"));

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }

        /** The device language when it is one we ship, otherwise Uzbek. */
        fun deviceDefault(): AppLanguage {
            val code = Locale.getDefault().language
            return fromCode(code) ?: UZ
        }
    }
}

/**
 * English is the source language, like on the web. Translations live in
 * assets/uz.json and assets/ru.json, keyed by the English text, so the same
 * copy can be shared with the web app's lib/i18n files.
 */
object Localizer {
    private val state = MutableStateFlow(AppLanguage.UZ)
    val languageFlow: StateFlow<AppLanguage> = state.asStateFlow()

    /** Compose observes this, so every screen that calls L() recomposes when the language changes. */
    private var observable by mutableStateOf(AppLanguage.UZ)

    @Volatile
    private var table: Map<String, String> = emptyMap()

    @Volatile
    private var loader: (AppLanguage) -> Map<String, String> = { emptyMap() }

    val language: AppLanguage get() = observable

    /** Sets where translation tables come from (the app's assets, or files in tests). */
    fun install(loader: (AppLanguage) -> Map<String, String>, language: AppLanguage) {
        this.loader = loader
        set(language)
    }

    fun set(language: AppLanguage) {
        table = if (language == AppLanguage.EN) emptyMap() else loader(language)
        state.value = language
        observable = language
    }

    fun translate(key: String): String {
        if (language == AppLanguage.EN) return key
        return table[key] ?: key
    }

    fun hasTranslation(key: String): Boolean = language == AppLanguage.EN || table.containsKey(key)

    fun parseTable(text: String): Map<String, String> =
        Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), text)
}

/**
 * Translates an English source string and fills `{name}` placeholders.
 *
 *     L("Order #{number}", mapOf("number" to 1042))
 */
fun L(key: String, values: Map<String, Any?> = emptyMap()): String {
    var text = Localizer.translate(key)
    for ((name, value) in values) {
        text = text.replace("{$name}", value.toString())
    }
    return text
}

/**
 * Picks the singular or plural English key, then translates it. Uzbek and
 * Russian translations are written so one form reads correctly for any count.
 */
fun L(one: String, other: String, count: Int, values: Map<String, Any?> = emptyMap()): String =
    L(if (count == 1) one else other, values + ("count" to count))

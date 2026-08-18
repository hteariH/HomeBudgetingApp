package com.personalmoney.core.locale

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Wraps the AppCompat per-app locale API. On API 33+ Android handles persistence
 * natively; below that, AppCompat's [androidx.appcompat.app.AppLocalesMetadataHolderService]
 * does the same (enabled in the manifest with `autoStoreLocales=true`).
 *
 * The system-locale-aware filtering for the picker lives here so the policy is in
 * one place rather than spread across the screen.
 */
object LocaleManager {

    /** Supported languages, declared in `app/build.gradle.kts` `resourceConfigurations`. */
    enum class Lang(val tag: String, val labelRes: Int) {
        EN("en", com.personalmoney.R.string.lang_english),
        RU("ru", com.personalmoney.R.string.lang_russian),
        UK("uk", com.personalmoney.R.string.lang_ukrainian);

        companion object {
            fun fromTag(tag: String?): Lang? = entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
        }
    }

    /** The locale Android exposes as the device default, regardless of in-app override. */
    fun systemLocale(): Locale =
        Resources.getSystem().configuration.locales[0] ?: Locale.ENGLISH

    /**
     * The currently applied in-app language tag, or `null` when "Follow system" is active.
     */
    fun currentTag(): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return null
        return locales[0]?.language
    }

    /**
     * Selectable languages for the picker. The rule the user asked for:
     * **when the device language is Ukrainian, never offer Russian** as a choice
     * (unless it is currently the active selection — we don't want to silently strand
     * the user with no matching chip).
     */
    fun availableLanguages(): List<Lang> {
        val system = systemLocale().language.lowercase()
        val current = currentTag()?.lowercase()
        val all = Lang.entries.toList()
        return all.filter { lang ->
            when {
                lang == Lang.RU && system == "uk" && current != "ru" -> false
                else -> true
            }
        }
    }

    /** Apply a language. Pass `null` to clear and follow the system. */
    fun apply(tag: String?) {
        val locales = if (tag.isNullOrBlank()) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

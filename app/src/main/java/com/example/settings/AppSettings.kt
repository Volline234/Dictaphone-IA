package com.example.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/** Display theme chosen by the user. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Interface language chosen by the user.
 * [SYSTEM] follows the device language (French devices -> French, everything else -> English).
 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    FRENCH("fr"),
    ENGLISH("en")
}

/**
 * Persists user display preferences (theme + interface language).
 * Uses the same SharedPreferences file as the rest of the app.
 */
object AppSettings {
    private const val PREFS_NAME = "dictaphone_app_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_APP_LANGUAGE = "app_language"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): ThemeMode {
        val stored = prefs(context).getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getLanguage(context: Context): AppLanguage {
        val stored = prefs(context).getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.name)
        return AppLanguage.entries.firstOrNull { it.name == stored } ?: AppLanguage.SYSTEM
    }

    /** Uses commit() so the value is on disk before the activity is recreated. */
    fun setLanguage(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_APP_LANGUAGE, language.name).commit()
    }
}

/**
 * Applies the in-app language without extra dependencies (works on API 24+).
 *
 * - Activities wrap their base context with [wrap] in attachBaseContext().
 * - ViewModels / non-UI classes use [localizedContext] to read strings in the chosen language.
 */
object LocaleHelper {

    /** Device locale captured from the system resources (never modified by the app). */
    private val systemLocale: Locale
        get() = Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()

    /** Locale that should currently be used for the UI. */
    fun resolveLocale(context: Context): Locale {
        return when (val lang = AppSettings.getLanguage(context)) {
            AppLanguage.SYSTEM -> systemLocale
            else -> Locale.forLanguageTag(lang.tag)
        }
    }

    /** Returns a context whose resources use the selected language. */
    fun wrap(base: Context): Context {
        val locale = resolveLocale(base)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** Same as [wrap]; name used by ViewModels for readability. */
    fun localizedContext(context: Context): Context = wrap(context)

    /**
     * Two-letter code ("fr" or "en") of the effective UI language.
     * Used as the default language for AI deliverables and speech recognition.
     */
    fun effectiveLanguageCode(context: Context): String {
        return if (resolveLocale(context).language == "fr") "fr" else "en"
    }
}

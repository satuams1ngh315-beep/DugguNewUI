package com.duggustore.app.platform

import android.content.Context
import android.content.res.Configuration
import com.duggustore.app.data.local.AppPrefs
import java.util.Locale

/** The languages the app ships translations for. */
enum class AppLanguage(val tag: String, val label: String, val short: String) {
    English("en", "English", "Eng"),
    Hindi("hi", "हिन्दी", "हिं"),
    Punjabi("pa", "ਪੰਜਾਬੀ", "ਪੰਜ");

    companion object {
        fun fromTag(tag: String?): AppLanguage? = values().firstOrNull { it.tag == tag }

        /** What the app is currently running in, resolved against the device. */
        fun current(context: Context): AppLanguage {
            AppPrefs.language(context)?.let { stored ->
                fromTag(stored)?.let { return it }
            }
            val deviceTag = context.resources.configuration.locales
                .takeIf { !it.isEmpty }?.get(0)?.language
            return fromTag(deviceTag) ?: English
        }
    }
}

/**
 * Wraps a context in the chosen locale. Called from Activity.attachBaseContext,
 * so every resource the activity resolves — including everything Compose reads
 * through stringResource — comes back in that language.
 */
fun Context.withAppLanguage(): Context {
    val tag = AppPrefs.language(this) ?: return this
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(config)
}

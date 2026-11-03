package com.jcapps.adbremote.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.R
import java.util.Locale

/**
 * Wraps [context] with the stored UI language ([resolveLanguage]) below Android 13
 * (Android 13+ uses per-app locales).
 */
// obf: AbstractC3179.m5627
fun wrapLocale(context: Context?): Context? {
    if (context == null || Build.VERSION.SDK_INT >= 33) {
        return context
    }
    val locale = Locale(resolveLanguage(context))
    Locale.setDefault(locale)
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(locale)
    return context.createConfigurationContext(configuration)
}

/**
 * The "language" preference; defaults to the device language if it is one of the supported
 * languages (R.array.en_my_entries), else "en".
 */
// obf: AbstractC3179.m5672
fun resolveLanguage(context: Context): String {
    var language = Locale.getDefault().language
    val supported = context.resources.getStringArray(R.array.en_my_entries)
    if (language !in supported) {
        language = "en"
    }
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    return prefs.getString("language", language) ?: language
}

package dev.adbremote.navhelper

import android.content.Context

/** Settings shared by the service and the settings screen. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("navhelper", Context.MODE_PRIVATE)

    /** Apps the helper drives; everywhere else the remote's keys pass through untouched. */
    var targets: Set<String>
        get() = sp.getStringSet(KEY_TARGETS, DEFAULT_TARGETS) ?: DEFAULT_TARGETS
        set(value) = sp.edit().putStringSet(KEY_TARGETS, value).apply()

    /** Debug overlay: all candidates, scroll containers and a key/status log. */
    var debug: Boolean
        get() = sp.getBoolean(KEY_DEBUG, false)
        set(value) = sp.edit().putBoolean(KEY_DEBUG, value).apply()

    companion object {
        private const val KEY_TARGETS = "targets"
        private const val KEY_DEBUG = "debug"
        val DEFAULT_TARGETS = setOf("com.netflix.mediaclient")
    }
}

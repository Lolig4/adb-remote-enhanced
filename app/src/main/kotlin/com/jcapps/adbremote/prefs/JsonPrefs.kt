package com.jcapps.adbremote.prefs

import android.content.SharedPreferences
import com.google.gson.Gson
import com.jcapps.adbremote.utils.SecureStorage

/**
 * Base for the JSON-in-SharedPreferences stores ([DevicesStore], [AppPrefs], ...):
 * serializes values with [gson] and writes them to [dataStore], optionally encrypted with
 * [SecureStorage].
 */
open class JsonPrefs(
    // obf: f5517
    val dataStore: SharedPreferences,
) {
    // obf: C1047

    // obf: f5516
    val gson: Gson = Gson()

    /** Stores [value] as JSON encrypted with the Android-Keystore key under [key]. */
    // obf: m3022
    fun putEncrypted(value: Any?, key: String) {
        val json = gson.toJson(value)
        val encrypted = SecureStorage.encrypt(json!!)
        dataStore.edit().putString(key, encrypted).apply()
    }

    /** Stores [value] as plain JSON under [key]. */
    // obf: m3023
    fun put(value: Any?, key: String) {
        dataStore.edit().putString(key, gson.toJson(value)).apply()
    }
}

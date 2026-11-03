package com.jcapps.adbremote.prefs

import android.content.SharedPreferences
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.jcapps.adbremote.model.AppConfig
import com.jcapps.adbremote.utils.SecureStorage

/** Holds the remote [AppConfig] (encrypted JSON under "app_config"), cached in memory. */
class AppPrefs(
    dataStore: SharedPreferences,
) : JsonPrefs(dataStore) {
    // obf: C3907

    // obf: f13747
    @Volatile
    private var cachedConfig: AppConfig? = null

    /** The stored [AppConfig], or a default one when unset / undecryptable / unparsable. */
    // obf: m6507
    val config: AppConfig
        get() = cachedConfig ?: synchronized(this) {
            cachedConfig ?: run {
                val stored = dataStore.getString(KEY_APP_CONFIG, null)?.let { encrypted ->
                    SecureStorage.decrypt(encrypted)?.let { json ->
                        try {
                            // obf: C2512
                            gson.fromJson<AppConfig>(json, object : TypeToken<AppConfig>() {}.type)
                        } catch (e: JsonSyntaxException) {
                            null
                        }
                    }
                }
                stored?.also { cachedConfig = it } ?: AppConfig().also { cachedConfig = it }
            }
        }

    /** Replaces the cached config and stores it encrypted. (inlined into C0965; leftover "config") — added by main-a */
    fun saveConfig(config: AppConfig) {
        synchronized(this) {
            cachedConfig = config
            putEncrypted(config, KEY_APP_CONFIG)
        }
    }

    companion object {
        const val KEY_APP_CONFIG = "app_config"
    }
}

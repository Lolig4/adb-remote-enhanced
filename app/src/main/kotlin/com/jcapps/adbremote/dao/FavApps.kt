package com.jcapps.adbremote.dao

import android.content.SharedPreferences
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.jcapps.adbremote.model.App
import com.jcapps.adbremote.prefs.JsonPrefs

/**
 * Favourite apps per device, stored as a JSON list of [App] under `"fav_apps_" + ipAddress`.
 *
 * Only [getKey] (m5082) and [getApps] (m5083) survived as real methods. All other members were
 * R8-inlined into their callers (AppsActivity, DialogDevices, SettingsActivity); they are restored
 * here because the inlined code keeps their parameter-name checks ("app", "oldIpAddress",
 * "newIpAddress") and always goes through a fresh `FavApps(dataStore)` instance.
 * RECON-UNSURE: names of the inlined members.
 */
class FavApps(dataStore: SharedPreferences) : JsonPrefs(dataStore) {
    // obf: C2670

    /** Returns the favourite apps of the device [ipAddress] (empty if none / unparsable). */
    // obf: m5083
    fun getApps(ipAddress: String?): List<App> {
        var apps: List<App>? = null
        val json = dataStore.getString(getKey(ipAddress), null)
        if (json != null) {
            try {
                // obf: C1012 = TypeToken<List<App>>
                apps = gson.fromJson(json, object : TypeToken<List<App>>() {}.type)
            } catch (_: JsonSyntaxException) {
            }
        }
        return apps ?: emptyList()
    }

    /** (inlined at AppsActivity, ADD_TO_FAV_APPS) */
    fun addApp(ipAddress: String?, app: App) {
        val apps = getApps(ipAddress).toMutableList()
        apps.add(app)
        put(apps, getKey(ipAddress))
    }

    /** (inlined at AppsActivity, REMOVE_FROM_FAV_APPS) */
    fun removeApp(ipAddress: String?, app: App) {
        val apps = getApps(ipAddress).toMutableList().filter { it.packageName != app.packageName }
        put(apps, getKey(ipAddress))
    }

    /** Moves the favourites of a device whose IP/serial changed. (inlined at DialogDevices, C1462) */
    fun moveApps(oldIpAddress: String, newIpAddress: String) {
        val json = dataStore.getString(getKey(oldIpAddress), null)
        if (json != null) {
            dataStore.edit()
                .remove(getKey(oldIpAddress))
                .putString(getKey(newIpAddress), json)
                .apply()
        }
    }

    /** Removes the favourites of a deleted device. (inlined at DialogDevices, C2552) */
    fun removeApps(ipAddress: String?) {
        dataStore.edit().remove(getKey(ipAddress)).apply()
    }

    /** Removes the favourites of all devices. (inlined at SettingsActivity, C2247 case: reset devices) */
    fun clear() {
        val all = dataStore.all
        val editor = dataStore.edit()
        for (key in all.keys) {
            if (key.startsWith(PREFIX)) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    companion object {
        // obf: <clinit> literal "fav_apps"
        const val PREFIX = "fav_apps"

        // obf: m5082
        @JvmStatic
        fun getKey(ipAddress: String?): String = "fav_apps_$ipAddress"
    }
}

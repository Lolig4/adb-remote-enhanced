package com.jcapps.adbremote.prefs

import android.content.SharedPreferences
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.jcapps.adbremote.model.AdbDevice

/**
 * Stores the saved ADB device list as JSON under the `adb_devices` key, on top of the shared
 * JSON-prefs base ([JsonPrefs], core-app, which provides the SharedPreferences + Gson).
 *
 * The `TypeToken<List<AdbDevice>>` (obf C3575 / `AdbDeviceListType`) is inlined here as an
 * anonymous `object : TypeToken<...>() {}` rather than a standalone class, per the registry note.
 */
// obf: C1963
class DevicesStore(
    dataStore: SharedPreferences,
) : JsonPrefs(dataStore) {

    // obf: m4138 — all saved devices (empty list when unset or on JSON parse error)
    fun getDevices(): List<AdbDevice> {
        var devices: List<AdbDevice>? = null
        val json = dataStore.getString(KEY_ADB_DEVICES, null)
        if (json != null) {
            try {
                // obf: C3575
                devices = gson.fromJson(json, object : TypeToken<List<AdbDevice>>() {}.type)
            } catch (e: JsonSyntaxException) {
            }
        }
        return devices ?: emptyList()
    }

    // obf: m4139 — the device flagged isDefault, or null
    fun getDefault(): AdbDevice? = getDevices().firstOrNull { it.isDefault }

    companion object {
        const val KEY_ADB_DEVICES = "adb_devices"
    }
}

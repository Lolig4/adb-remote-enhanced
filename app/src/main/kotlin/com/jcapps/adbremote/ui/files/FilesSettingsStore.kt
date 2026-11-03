package com.jcapps.adbremote.ui.files

import android.content.Context
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.jcapps.adbremote.model.FilesSettings

/*
 * File facade AbstractC3930 (its only member). Also used by DialogFilesSettings (C1389).
 * RECON-UNSURE: file/function name.
 */

/** Reads the "files_settings_open" JSON (default [FilesSettings] when unset or invalid). */
// obf: AbstractC3930.m6533
fun loadFilesSettings(context: Context): FilesSettings {
    val dataStore = PreferenceManager.getDefaultSharedPreferences(context)
    val gson = Gson()
    var settings: FilesSettings? = null
    val json = dataStore.getString("files_settings_open", null)
    if (json != null) {
        try {
            // obf: C0766
            settings = gson.fromJson(json, object : TypeToken<FilesSettings>() {}.type)
        } catch (e: JsonSyntaxException) {
        }
    }
    return settings ?: FilesSettings()
}

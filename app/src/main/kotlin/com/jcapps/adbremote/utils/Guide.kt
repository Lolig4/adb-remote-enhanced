package com.jcapps.adbremote.utils

import android.content.Context
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.model.Link
import com.jcapps.adbremote.prefs.AppPrefs

/*
 * App top-level helpers for the website guide pages (merged into helper class `b` by R8).
 * Claimed by group main-b (b.m996 / b.m1033).
 */

/** `<preferredWebsite>/<path>`, or `<preferredWebsite>/<lang>/<path>` for non-English UI languages. */
// obf: b.m996
fun getGuideUrl(context: Context, path: String): String {
    val website = AppPrefs(PreferenceManager.getDefaultSharedPreferences(context)).config.preferredWebsite
    val language = resolveLanguage(context)
    if (language == "en") {
        return "$website/$path"
    }
    return "$website/$language/$path"
}

/** Opens the guide page [path] (see [getGuideUrl]) in the browser. */
// obf: b.m1033
fun openGuide(context: Context, path: String) {
    with(PlayStore) {
        context.launchLink(Link(getGuideUrl(context, path), null))
    }
}

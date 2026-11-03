package com.jcapps.adbremote.utils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat

/** Applies the "theme" preference value ("light" / "dark" / "system") as default night mode. */
// obf: AbstractC3745.m6302
fun applyTheme(theme: String?) {
    val mode = when (theme) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        else -> throw IllegalStateException()
    }
    AppCompatDelegate.setDefaultNightMode(mode)
}

/** Resolves a theme color attribute (resource reference or literal color). RECON-UNSURE name. */
// obf: AbstractC3745.m6311
fun Context.getColorFromAttr(@AttrRes attr: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    var colorRes = typedValue.resourceId
    if (colorRes == 0) {
        colorRes = typedValue.data
    }
    return ContextCompat.getColor(this, colorRes)
}

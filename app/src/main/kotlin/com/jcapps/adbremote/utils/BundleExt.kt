package com.jcapps.adbremote.utils

import android.os.Build
import android.os.Bundle
import android.os.Parcelable

/*
 * App top-level Bundle helper (merged into helper class `z8` by R8). Claimed by group main-b.
 */

/** [Bundle.getParcelable] that is type-safe on all API levels. */
// obf: z8.m1616
fun <T : Parcelable> Bundle.getParcelableCompat(key: String, clazz: Class<T>): T? {
    if (Build.VERSION.SDK_INT >= 34) {
        return getParcelable(key, clazz)
    }
    @Suppress("DEPRECATION")
    val parcelable = getParcelable<Parcelable>(key)
    if (clazz.isInstance(parcelable)) {
        @Suppress("UNCHECKED_CAST")
        return parcelable as T
    }
    return null
}

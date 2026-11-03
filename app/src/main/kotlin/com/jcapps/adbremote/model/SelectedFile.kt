package com.jcapps.adbremote.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A file/folder the user has selected (for copy/cut/move). Property order follows the original
 * Kotlin data class (toString + Parcel write both run path, isDirectory, name).
 */
@Parcelize
data class SelectedFile(
    // obf: f3529
    val path: String,
    // obf: f3531
    val isDirectory: Boolean,
    // obf: f3530
    val name: String,
) : Parcelable {
    // obf: C0311
}

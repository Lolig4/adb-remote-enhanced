package com.jcapps.adbremote.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.ContextCompat

// RECON-UNSURE: names of these three app top-level helpers (R8-merged into AbstractC0913).

// obf: AbstractC0913.m2774
fun Context.getClipboardManager(): ClipboardManager =
    ContextCompat.getSystemService(this, ClipboardManager::class.java)!!

// obf: AbstractC0913.m2771
fun Context.getClipboardText(): String {
    val primaryClip = getClipboardManager().primaryClip
    return if (primaryClip == null || primaryClip.itemCount <= 0) {
        ""
    } else {
        primaryClip.getItemAt(0).coerceToText(this).toString()
    }
}

// obf: AbstractC0913.m2775
fun Context.copyToClipboard(str: String) {
    getClipboardManager().setPrimaryClip(ClipData.newPlainText("text", str))
}

package com.jcapps.adbremote.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/*
 * App top-level File helper (merged into helper class `AbstractC1557` by R8). Claimed by group main-b.
 */

/** Content URI of this file via the app's `<package>.provider` FileProvider, or null on failure. */
// obf: AbstractC1557.m3535 — RECON-UNSURE: name (guide mentions "File.toUri")
fun File.toUri(context: Context): Uri? =
    runCatching { FileProvider.getUriForFile(context, context.packageName + ".provider", this) }.getOrNull()

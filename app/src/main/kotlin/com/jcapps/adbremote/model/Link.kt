package com.jcapps.adbremote.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/** A primary [url] (often a deep link) with an optional web [fallbackUrl]. */
// obf: C1743
@Parcelize
data class Link(
    // obf: f7422
    @SerializedName("url") val url: String,
    // obf: f7423
    @SerializedName("fallback_url") val fallbackUrl: String?,
) : Parcelable

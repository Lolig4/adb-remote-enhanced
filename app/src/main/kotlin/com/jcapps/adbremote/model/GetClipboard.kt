package com.jcapps.adbremote.model

import com.google.gson.annotations.SerializedName

/** Response of the on-device server's clipboard endpoint (`ApiService.getClipboardText`). */
data class GetClipboard(
    // obf: f8849 / m4440
    @SerializedName("has_image") val hasImage: Boolean,
    // obf: f8848 / m4439
    @SerializedName("text") val text: String,
) {
    // obf: C2234
}

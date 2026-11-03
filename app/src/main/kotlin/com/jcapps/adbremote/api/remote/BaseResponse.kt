package com.jcapps.adbremote.api.remote

import com.google.gson.annotations.SerializedName

/** Generic envelope returned by the on-device ADB server API ([ApiService]). */
// obf: C3618
data class BaseResponse<Data>(
    // obf: f12907 / m6228
    @SerializedName("data") val data: Data?,
    // obf: f12906 / m6229
    @SerializedName("status") val status: Boolean,
    // obf: f12908 / m6227
    @SerializedName("message") val message: String?,
)

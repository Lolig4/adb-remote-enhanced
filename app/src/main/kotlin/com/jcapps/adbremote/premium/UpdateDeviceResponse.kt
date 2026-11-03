package com.jcapps.adbremote.premium

import com.google.gson.annotations.SerializedName

/** Response of `POST update`. Built by Gson only. */
data class UpdateDeviceResponse(
    // obf: f2089 / m1313
    @SerializedName("status") val status: Boolean,
    // obf: f2088 / m1314
    @SerializedName("message") val message: String?,
) {
    // obf: l0
}

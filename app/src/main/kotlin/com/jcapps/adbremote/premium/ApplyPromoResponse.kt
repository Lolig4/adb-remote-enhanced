package com.jcapps.adbremote.premium

import com.google.gson.annotations.SerializedName

/** Response of `POST apply`. Built by Gson only. */
data class ApplyPromoResponse(
    // obf: f9591 / m4806
    @SerializedName("status") val status: Boolean,
    // obf: f9590 / m4805
    @SerializedName("ends_at") val endsAt: Long?,
    // obf: f9592 / m4804
    @SerializedName("message") val message: String?,
) {
    // obf: C2522
}

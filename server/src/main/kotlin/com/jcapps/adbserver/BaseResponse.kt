package com.jcapps.adbserver

import com.google.gson.annotations.SerializedName

/**
 * Envelope of every JSON response: `{"status": true, "data": ...}` on success,
 * `{"status": false, "message": "..."}` on error (Gson's default drops the null field).
 */
// obf: C0190 (f417 = data, f416 = status, f418 = message)
data class BaseResponse<Data>(
    @field:SerializedName("data") val data: Data?,
    @field:SerializedName("status") val status: Boolean,
    @field:SerializedName("message") val message: String?,
)

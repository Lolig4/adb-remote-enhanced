package com.jcapps.adbremote.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

// obf: C2639
@Parcelize
data class AdbDevice(
    // obf: f9899
    @SerializedName("name") val name: String,
    // obf: f9903
    @SerializedName("serial") val serial: String,
    // obf: f9901
    @SerializedName("ip_address") val ipAddress: String,
    // obf: f9900
    @SerializedName("port") val port: Int,
    // obf: f9902
    @SerializedName("is_default") var isDefault: Boolean,
) : Parcelable {
    // obf: m5044
    fun unsetDefault() {
        isDefault = false
    }
}

package com.jcapps.adbremote.premium

import com.google.gson.annotations.SerializedName
import com.jcapps.adbremote.BuildConfig

/** Body of `POST update` ([com.jcapps.adbremote.api.remote.PremiumService]). */
data class UpdateDeviceRequest(
    // obf: f2037
    @SerializedName("promo_code") val promoCode: String,
    // obf: f2036
    @SerializedName("device_id") val deviceId: String,
    // obf: f2039 — constant-folded to 6 by R8 (= versionCode 6).
    // RECON-UNSURE: original most likely `BuildConfig.VERSION_CODE` as a property initializer.
    @SerializedName("app_version") val appVersion: Int = BuildConfig.VERSION_CODE,
    // obf: f2038
    @SerializedName("device_version") val deviceVersion: Int,
    // obf: f2041
    @SerializedName("device_name") val deviceName: String,
    // obf: f2040
    @SerializedName("logout") val isLogout: Boolean,
) {
    // obf: k0
}

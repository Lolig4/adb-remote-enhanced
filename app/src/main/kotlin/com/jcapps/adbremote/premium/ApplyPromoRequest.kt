package com.jcapps.adbremote.premium

import com.google.gson.annotations.SerializedName
import com.jcapps.adbremote.BuildConfig

/** Body of `POST apply` ([com.jcapps.adbremote.api.remote.PremiumService]). */
data class ApplyPromoRequest(
    // obf: f8394
    @SerializedName("promo_code") val promoCode: String,
    // obf: f8393
    @SerializedName("device_id") val deviceId: String,
    // obf: f8396 — constant-folded to 6 by R8 (= versionCode 6).
    // RECON-UNSURE: original most likely `BuildConfig.VERSION_CODE` as a property initializer.
    @SerializedName("app_version") val appVersion: Int = BuildConfig.VERSION_CODE,
    // obf: f8395 — passed by the caller (presumably Build.VERSION.SDK_INT)
    @SerializedName("device_version") val deviceVersion: Int,
    // obf: f8397
    @SerializedName("device_name") val deviceName: String,
) {
    // obf: C2079
}

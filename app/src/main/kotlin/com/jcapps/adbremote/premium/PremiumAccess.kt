package com.jcapps.adbremote.premium

import com.google.gson.annotations.SerializedName

/**
 * Locally persisted premium entitlement of this device.
 *
 * The `@SerializedName` keys are single Myanmar letters in the original (obfuscation of the
 * stored JSON) and must be kept exactly.
 */
data class PremiumAccess(
    // obf: f5150 / m2823
    @SerializedName("က") val deviceId: String,
    // obf: f5149 / m2824
    @SerializedName("ခ") val promoEndsAt: Long,
    // obf: f5152 / m2827
    @SerializedName("ဂ") val totalEndsAt: Long,
    // obf: f5151 / m2826
    @SerializedName("ဃ") val isLifetime: Boolean,
    // obf: f5153 / m2825
    @SerializedName("င") val promoCode: String?,
) {
    // obf: C0918
    // NOTE: obf static m2822(PremiumAccess, long) is an R8-specialised call site of
    //  `copy(promoEndsAt = 0L, totalEndsAt = <long>, isLifetime = false, promoCode = null)` —
    //  written inline at the caller, not a member here.
}

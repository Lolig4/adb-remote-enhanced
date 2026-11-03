package com.jcapps.adbremote.premium

/** A lifetime promo code grant. */
data class LifetimePromo(
    // obf: f4043
    val promoCode: String,
) : PremiumGrant() {
    // obf: C0491
}

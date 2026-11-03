package com.jcapps.adbremote.premium

/** A time-boxed promo code grant. */
data class Promo(
    // obf: f7584
    val promoCode: String,
    // obf: f7583
    val endsAt: Long,
) : PremiumGrant() {
    // obf: C1811
}

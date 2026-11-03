package com.jcapps.adbremote.premium

/**
 * A premium entitlement to apply: an ad [Reward], a time-boxed [Promo] code, or a [LifetimePromo].
 *
 * RECON-UNSURE (name): the original sealed parent was R8 class-merged into an unrelated static
 * utility bag (obf `AbstractC0142`); only its subtype role is recoverable. The static helper
 * methods merged into that alias are inlined at their use sites, not ported here.
 */
// obf: AbstractC0142 (type role only)
sealed class PremiumGrant

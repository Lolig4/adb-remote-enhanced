package com.jcapps.adbremote.ntp

/**
 * Wraps the configured [InMemorySntpCache] for [SntpClient]. RECON-UNSURE (name/members): the
 * wrapper's methods were inlined into SntpClient by R8; only the wrapped [cache] survives.
 */
class SntpCacheManager(
    // obf: f6763
    val cache: InMemorySntpCache,
) {
    // obf: C1518 (tag 6; R8-merged class, ctor C1518(cache, 6))
}

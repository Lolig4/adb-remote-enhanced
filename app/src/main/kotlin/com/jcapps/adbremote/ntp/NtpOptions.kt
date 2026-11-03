package com.jcapps.adbremote.ntp

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.time.Duration

/**
 * Configuration of [SntpClient].
 *
 * R8 constant-folded and removed the properties that always kept their defaults; their values
 * survive only in the generated `toString` ("listener=null, maxRetryPerHost=0,
 * maxRetryLoop=2147483647, rootDelayMax=100, rootDispersionMax=100") and are restored here as
 * defaulted properties in the original order.
 */
data class NtpOptions(
    // obf: f13243
    val syncDispatcher: CoroutineDispatcher,
    // obf: f13242
    val listenerDispatcher: CoroutineDispatcher? = null,
    // (removed by R8, always null)
    val listener: SntpClient.Listener? = null,
    // obf: f13247
    val cache: InMemorySntpCache,
    // obf: f13245 — monotonic clock in ms (SystemClock.elapsedRealtime)
    val elapsedTime: () -> Long,
    // obf: f13249
    val connectionTimeout: Duration,
    // obf: f13248
    val ntpHosts: List<String>,
    // (removed by R8, always 0)
    val maxRetryPerHost: Int = 0,
    // (removed by R8, always Int.MAX_VALUE)
    val maxRetryLoop: Int = Int.MAX_VALUE,
    // obf: f13244
    val delayBetweenRetryLoop: Duration,
    // (removed by R8, always 100)
    val rootDelayMax: Int = 100,
    // (removed by R8, always 100)
    val rootDispersionMax: Int = 100,
    // obf: f13246
    val serverResponseDelayMax: Duration,
) {
    // obf: C3741
    // RECON-UNSURE: the R8 output types `ntpHosts` as ArrayList and `cache` as the merged
    //  C1518; the original may have used an interface type for the cache.
}

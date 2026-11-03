package com.jcapps.adbremote.ntp

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import org.apache.commons.net.ntp.NTPUDPClient
import org.apache.commons.net.ntp.TimeInfo
import java.net.InetAddress
import kotlin.math.abs

/**
 * SNTP client (Kronos-style validation on top of commons-net [NTPUDPClient]) with an in-memory
 * cache of the last [SafeTimeInfo] and a coroutine-based background [sync].
 *
 * NOTE: C0221 (registry "NtpSocket") is commons-net `NTPUDPClient` (with `DatagramSocketClient`
 * inlined) and C3319 is commons-net `TimeInfo`: library code, used directly.
 */
class SntpClient(
    // obf: f11755
    val options: NtpOptions,
) {
    // obf: C3283

    /** Sync result callbacks. RECON-UNSURE: the interface was removed by R8; the only
     *  implementation (C1171 case 23) wraps (Function1, Function0) from DialogPauseAd. */
    // RECON-UNSURE: method names. Slots recovered from the C2150 callback lambdas
    //  (C3765 / C0094 / C0705 / C1229); the three no-op ones were empty in C1171 case 23.
    interface Listener {
        // (C3765) one request failed
        fun onRequestFailed(host: String, retryCount: Int, loopCount: Int, e: Exception) {}

        // (C0094, first call)
        fun onSuccess(safeTimeInfo: SafeTimeInfo, host: String, retryCount: Int, loopCount: Int) {}

        // (C0094, second call)
        fun onSuccess(timeInfo: SafeTimeInfo)

        // (C0705) before waiting delayBetweenRetryLoop
        fun onRetryLoop(loopCount: Int, delay: Duration) {}

        // (C1229) all hosts / loops exhausted
        fun onError(t: Throwable)
    }

    // obf: f11754
    private val cacheManager = SntpCacheManager(options.cache)

    // obf: f11756
    private var syncJob: Job? = null

    /** One validated SNTP request to [host]. Throws [InvalidNtpServerResponseException]. */
    // obf: m5837
    fun requestTime(host: String): SafeTimeInfo {
        val address = InetAddress.getByName(host)
        val client = NTPUDPClient()
        client.setDefaultTimeout(options.connectionTimeout.inWholeMilliseconds.toInt())
        val requestTime = System.currentTimeMillis()
        val requestTicks = options.elapsedTime()
        val timeInfo: TimeInfo = client.use { it.getTime(address) }
        val responseTicks = options.elapsedTime()
        val message = timeInfo.message
        val rootDelay = message.rootDelay
        if (rootDelay > options.rootDelayMax) {
            throw InvalidNtpServerResponseException(
                rootDelay, options.rootDelayMax,
                "Invalid response from NTP server. %s violation. %d [actual] > %d [expected]",
                "root_delay",
            )
        }
        val rootDispersion = message.rootDispersion
        if (rootDispersion > options.rootDispersionMax) {
            throw InvalidNtpServerResponseException(
                rootDispersion, options.rootDispersionMax,
                "Invalid response from NTP server. %s violation. %d [actual] > %d [expected]",
                "root_dispersion",
            )
        }
        val mode = message.mode
        if (mode !in listOf(4, 5)) {
            throw InvalidNtpServerResponseException("Untrusted mode value: $mode")
        }
        val stratum = message.stratum
        if (stratum !in 1..15) {
            throw InvalidNtpServerResponseException("Untrusted stratum value: $stratum")
        }
        if (message.leapIndicator == 3) {
            throw InvalidNtpServerResponseException("Unsynchronized server response received. [leapIndicator: 3]")
        }
        val originateTime = message.originateTimeStamp.time
        val receiveTime = message.receiveTimeStamp.time
        val transmitTime = message.transmitTimeStamp.time
        val responseTime = (responseTicks - requestTicks) + requestTime
        val delay = abs((responseTime - originateTime) - (transmitTime - receiveTime))
        val serverResponseDelayMax = options.serverResponseDelayMax.inWholeMilliseconds
        if (delay >= serverResponseDelayMax) {
            throw InvalidNtpServerResponseException(
                delay.toInt(), serverResponseDelayMax.toInt(),
                "%s too large for comfort %d [actual] >= %d [expected]",
                "server_response_delay",
            )
        }
        val originateDiff = abs(originateTime - System.currentTimeMillis())
        if (originateDiff >= 10000) {
            throw InvalidNtpServerResponseException("Request was sent more than 10 seconds back $originateDiff")
        }
        val offset = ((transmitTime - responseTime) + (receiveTime - originateTime)) / 2
        return SafeTimeInfo(offset, responseTime + offset, responseTicks, timeInfo)
    }

    /**
     * The cached time advanced by the elapsed time since it was fetched, or null when nothing is
     * cached or the cache is invalid (device rebooted: response tick in the future → cleared).
     */
    // obf: m5838
    fun cachedTime(): SafeTimeInfo? {
        val now = options.elapsedTime()
        val cache = cacheManager.cache
        val cached = cache.timeInfo
        var valid = false
        if (cached != null) {
            val invalid = cached.responseTimestamp > now
            if (invalid) cache.timeInfo = null
            valid = !invalid
        }
        if (!valid) return null
        val info = cache.timeInfo!!
        val responseTimestamp = info.responseTimestamp
        return SafeTimeInfo(info.timeOffset, (now - responseTimestamp) + info.timestamp, responseTimestamp, info.timeInfo)
    }

    /**
     * Starts (replacing any previous) background sync over [NtpOptions.ntpHosts] on
     * [NtpOptions.syncDispatcher]; results are delivered to [listener] on
     * [NtpOptions.listenerDispatcher] (or the scope's context).
     */
    // obf: m5839
    fun sync(scope: CoroutineScope? = null, listener: Listener? = null): Job {
        if (options.ntpHosts.isEmpty()) {
            throw IllegalArgumentException("NTP Hosts should not be empty.")
        }
        val syncListener = listener ?: options.listener
        val syncScope = scope ?: CoroutineScope(SupervisorJob() + options.syncDispatcher)
        val listenerContext = options.listenerDispatcher ?: syncScope.coroutineContext
        val previousJob = syncJob
        val job = syncScope.launch(CoroutineName("SafeTime") + options.syncDispatcher) {
            // obf: C2150 (jadx failed; reconstructed from smali-dumps/C2150)
            previousJob?.cancelAndJoin()
            val hostsCount = options.ntpHosts.size
            var hostIndex = 0
            var retryCount = 0
            var loopCount = 0
            val delayBetweenRetryLoop = options.delayBetweenRetryLoop.inWholeMilliseconds
            var synced = false
            while (isActive) {
                val host = options.ntpHosts[hostIndex]
                var safeTimeInfo: SafeTimeInfo? = null
                try {
                    safeTimeInfo = requestTime(host)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // obf: C3765
                    withContext(listenerContext) {
                        syncListener?.onRequestFailed(host, retryCount, loopCount, e)
                    }
                }
                if (safeTimeInfo != null) {
                    cacheManager.cache.timeInfo = safeTimeInfo
                    // obf: C0094
                    withContext(listenerContext) {
                        syncListener?.onSuccess(safeTimeInfo, host, retryCount, loopCount)
                        syncListener?.onSuccess(safeTimeInfo)
                    }
                    synced = true
                    break
                }
                if (retryCount < options.maxRetryPerHost) {
                    retryCount++
                    continue
                }
                if (hostIndex == hostsCount - 1) {
                    if (loopCount == options.maxRetryLoop) break
                    loopCount++
                    hostIndex = 0
                    if (delayBetweenRetryLoop > 0) {
                        // obf: C0705
                        withContext(listenerContext) {
                            syncListener?.onRetryLoop(loopCount, options.delayBetweenRetryLoop)
                        }
                        delay(delayBetweenRetryLoop)
                    }
                } else {
                    hostIndex++
                }
                retryCount = 0
            }
            ensureActive()
            if (!synced) {
                // obf: C1229
                // RECON-UNSURE: exception class is the merged C1864 (message ctor); the original
                //  may have been a dedicated sync exception.
                withContext(listenerContext) {
                    syncListener?.onError(InvalidNtpServerResponseException("Failed to sync time."))
                }
            }
        }
        syncJob = job
        return job
    }
}

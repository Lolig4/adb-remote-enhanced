package com.jcapps.adbremote.ntp

import org.apache.commons.net.ntp.TimeInfo

/**
 * A validated NTP result: [timeOffset] (server - device, ms), [timestamp] (corrected wall time,
 * ms), [responseTimestamp] (elapsedTime() when the response arrived) and the raw [timeInfo].
 */
data class SafeTimeInfo(
    // obf: f10180
    val timeOffset: Long,
    // obf: f10179
    val timestamp: Long,
    // obf: f10182
    val responseTimestamp: Long,
    // obf: f10181 — C3319 = org.apache.commons.net.ntp.TimeInfo (library, see lib-notes)
    val timeInfo: TimeInfo?,
) {
    // obf: C2732
}

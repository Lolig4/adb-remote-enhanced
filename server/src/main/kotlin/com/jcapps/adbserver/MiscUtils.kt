package com.jcapps.adbserver

import android.os.Environment
import java.io.File

/**
 * Small helpers shared by the MISC handlers (FTP / NTP / intent). Kept in an object so no
 * package-level names can clash with other groups.
 */
internal object MiscUtils {

    /** Hostname: labels of 1..63 chars `[A-Za-z0-9-]`, no leading/trailing '-', separated by '.'. */
    // obf: AbstractC0346.f772 (Kotlin Regex, obf C0321 mode 1)
    val HOSTNAME_REGEX = Regex("^(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))*$")

    /** Dotted IPv4 address. */
    // obf: AbstractC0346.f771 (Kotlin Regex, obf C0321 mode 1)
    val IPV4_REGEX = Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$")

    /** Only IPv4 (used by /ftp/start). */
    // obf: inlined into C0171.mo143 (FTP branch); receiver check name "<this>", regex param "input"
    fun isValidIpAddress(address: String): Boolean = IPV4_REGEX.matches(address)

    /** NTP server validation used by POST /ntp: hostname (<= 253 chars), IPv4, or a simple IPv6 check. */
    // obf: inlined into C0171.mo143 (NTP SET branch)
    fun isValidNtpServer(server: String): Boolean {
        if (server.length <= 253 && HOSTNAME_REGEX.matches(server)) {
            return true
        }
        if (IPV4_REGEX.matches(server)) {
            return true
        }
        return isValidIpv6Address(server)
    }

    /**
     * IPv6 check (obf: inlined, smali `:cond_5 .. :cond_10` of C0171).
     *
     * BUG(original): at most one empty group is allowed, but `split(":")` yields *two* empty groups
     * for a leading or trailing `::` ("::1", "fe80::" ...), so those valid addresses are rejected.
     * IPv4-mapped forms ("::ffff:1.2.3.4") and zone ids ("%eth0") are rejected as well.
     */
    private fun isValidIpv6Address(address: String): Boolean {
        val groups = address.split(":")
        if (address.length == 0) {
            return false
        }
        val size = groups.size
        if (size < 3 || size >= 9) {
            return false
        }
        var emptyGroups = 0
        for (group in groups) {
            if (group.length == 0) {
                emptyGroups++
                if (emptyGroups > 1) {
                    return false
                }
            } else {
                val len = group.length
                if (len < 1 || len >= 5) {
                    return false
                }
                for (c in group) {
                    val hex = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
                    if (!hex) {
                        return false
                    }
                }
            }
        }
        // obf: Iterable.count { } (with its "Count overflow has happened." check)
        val nonEmptyGroups = groups.count { it.length > 0 }
        return nonEmptyGroups + emptyGroups <= 8
    }

    /**
     * Directory resolution used by /ftp/start (default_dir) and /intent (file_path.parent):
     * a non-blank [path] wins, else the external storage directory, else [fallback].
     */
    // obf: inlined twice (C0171.mo143 FTP branch, C0118.mo143); runCatching = AbstractC0353.m374 / C0301
    fun resolveDirectory(path: String?, fallback: () -> File): File {
        val dir = path?.takeUnless { it.isBlank() } // obf: AbstractC0265.m269 = isBlank
        if (dir != null) {
            return File(dir)
        }
        return runCatching { Environment.getExternalStorageDirectory() }.getOrNull() ?: fallback()
    }

    /** Swallows [Exception]s (not Errors), like the original helper. */
    // obf: AbstractC0353.m354
    inline fun ignoreExceptions(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
        }
    }
}

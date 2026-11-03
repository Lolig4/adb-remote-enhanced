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
     * Fixed (rebuild): the original counted empty groups of `split(":")` and so rejected valid
     * addresses with a leading/trailing `::` ("::1", "fe80::"), IPv4-mapped forms and zone ids.
     * A literal containing ':' is parsed without any DNS lookup.
     */
    private fun isValidIpv6Address(address: String): Boolean {
        if (!address.contains(':') || address.any { it.isWhitespace() || it == '/' }) {
            return false
        }
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.net.InetAddresses.isNumericAddress(address) &&
                    android.net.InetAddresses.parseNumericAddress(address) is java.net.Inet6Address
            } else {
                java.net.InetAddress.getByName(address) is java.net.Inet6Address
            }
        } catch (e: Exception) {
            false
        }
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

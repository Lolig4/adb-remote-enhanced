package com.jcapps.adbremote.provider.ftp

/**
 * Minimal "user@host:port" authority used when building a path URI. Rendered as
 * [username@]host[:port], omitting null parts.
 *
 * RECON-UNSURE: original name obfuscated (m0).
 */
data class FtpAuthorityString(
    // obf: f2104
    val username: String?,
    // obf: f2103
    val host: String,
    // obf: f2105
    val port: Int?,
) {
    // obf: m0

    override fun toString(): String = buildString {
        if (username != null) {
            append(username)
            append('@')
        }
        append(host)
        if (port != null) {
            append(':')
            append(port.toString())
        }
    }

    companion object {
        // obf: static init created FtpAuthorityString(null, "", null)
    }
}

package com.jcapps.adbremote.provider.ftp

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient

/**
 * FTP protocol variant with its default port and a factory for the matching commons-net client.
 *
 * ftps = implicit TLS (FTPSClient(true)), ftpes = explicit TLS (FTPSClient(false)) (confirmed by C3079).
 */
enum class FtpProtocol(
    // obf: f5450
    val scheme: String,
    // obf: f5452
    val defaultPort: Int,
    // obf: f5451
    val clientFactory: () -> FTPClient,
) {
    // obf: EnumC1024
    FTP("ftp", FTPClient.DEFAULT_PORT, ::FTPClient),       // f5448 (factory C0074.f2804)
    FTPS("ftps", FTPSClient.DEFAULT_FTPS_PORT, { FTPSClient(true) }), // f5444 (factory C3079(9))
    FTPES("ftpes", FTPClient.DEFAULT_PORT, { FTPSClient(false) }); // f5446 (factory C3079(10))

    companion object {
        // obf: f5445 — all schemes in declaration order
        @JvmField
        val schemes: List<String> = entries.map { it.scheme }

        // inlined into FtpFileSystemProvider (URI -> authority)
        fun fromScheme(scheme: String): FtpProtocol =
            entries.firstOrNull { it.scheme == scheme } ?: throw IllegalArgumentException(scheme)
    }
}

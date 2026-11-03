package com.jcapps.adbremote.provider.ftp

import com.jcapps.adbremote.provider.common.DelegateSchemeFileSystemProvider

// obf: AbstractC3850 — scheme aliases of [FtpFileSystemProvider] for implicit / explicit TLS.

// obf: AbstractC3850.f13602
val FtpsFileSystemProvider = DelegateSchemeFileSystemProvider(FtpProtocol.FTPS.scheme, FtpFileSystemProvider)

// obf: AbstractC3850.f13601
val FtpesFileSystemProvider = DelegateSchemeFileSystemProvider(FtpProtocol.FTPES.scheme, FtpFileSystemProvider)

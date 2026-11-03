package com.jcapps.adbremote.provider

import com.jcapps.adbremote.provider.common.AndroidFileTypeDetector
import com.jcapps.adbremote.provider.common.StubDefaultFileSystemProvider
import com.jcapps.adbremote.provider.ftp.FtpFileSystemProvider
import com.jcapps.adbremote.provider.ftp.FtpesFileSystemProvider
import com.jcapps.adbremote.provider.ftp.FtpsFileSystemProvider
import java8.nio.file.Files
import java8.nio.file.spi.FileSystemProvider

// obf: AbstractC0880 — startup initializers run by [AppProvider.onCreate] (MaterialFiles AppInitializers).

// obf: AbstractC0880.f5033
val appInitializers = listOf(::initializeFileSystemProviders)

// obf: C1703 (function reference "initializeFileSystemProviders()V")
fun initializeFileSystemProviders() {
    FileSystemProvider.installDefaultProvider(StubDefaultFileSystemProvider)
    FileSystemProvider.installProvider(FtpFileSystemProvider)
    FileSystemProvider.installProvider(FtpsFileSystemProvider)
    FileSystemProvider.installProvider(FtpesFileSystemProvider)
    Files.installFileTypeDetector(AndroidFileTypeDetector)
}

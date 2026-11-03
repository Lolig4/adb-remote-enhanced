package com.jcapps.adbremote.provider.common

import java8.nio.file.Path
import java8.nio.file.spi.FileTypeDetector

/**
 * File-type detector installed at startup (MaterialFiles AndroidFileTypeDetector).
 * RECON-UNSURE: R8 removed probeContentType (Files.probeContentType is never called); only the
 * singleton (with FileTypeDetector's security-manager check) survived. Returns null = unknown.
 */
object AndroidFileTypeDetector : FileTypeDetector() {
    // obf: C0116 (instance f2899)

    override fun probeContentType(path: Path): String? = null
}

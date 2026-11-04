package com.jcapps.adbremote.ui.install

import java.io.File

/** Rebuild addition (sideload feature): something that can be installed on the TV. */
sealed class ApkSource {
    abstract val label: String
    abstract val packageName: String?
    abstract val versionName: String?
    /** -1 when unknown (unparsable file). */
    abstract val versionCode: Long

    /** An app installed on this phone: its base APK plus split APKs. */
    data class PhoneApp(
        override val label: String,
        override val packageName: String,
        override val versionName: String?,
        override val versionCode: Long,
        val files: List<File>,
    ) : ApkSource()

    /** An .apk / .apks / .xapk / .apkm file on this phone ([deleteAfter] for picked copies in the cache). */
    data class PhoneFile(
        override val label: String,
        override val packageName: String?,
        override val versionName: String?,
        override val versionCode: Long,
        val file: File,
        val deleteAfter: Boolean = false,
    ) : ApkSource()

    /** A file in the TV's Download folder. */
    data class TvFile(
        override val label: String,
        override val packageName: String?,
        override val versionName: String?,
        override val versionCode: Long,
        val path: String,
    ) : ApkSource()
}

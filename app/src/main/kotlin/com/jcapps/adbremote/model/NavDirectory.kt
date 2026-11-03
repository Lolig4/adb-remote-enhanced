package com.jcapps.adbremote.model

import com.jcapps.adbremote.provider.ftp.FtpPath
import java8.nio.file.Path

/** One entry in the Files breadcrumb trail: a directory name and its FTP path (null = root list). */
data class NavDirectory(
    // obf: f8106
    val name: String,
    // obf: f8105
    val path: Path?,
) {
    // obf: C1985

    /** Resolve a child name against this directory's path. obf: m4163 */
    fun resolve(name: String): Path? = path?.let { (it as FtpPath).resolve(name) }
}

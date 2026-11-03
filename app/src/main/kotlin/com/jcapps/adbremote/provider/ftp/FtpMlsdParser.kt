package com.jcapps.adbremote.provider.ftp

import java.io.File
import java.io.IOException
import java.util.Calendar
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile

/**
 * MLST/MLSD-aware stat + listing helpers for commons-net (top-level FTPClient extensions in the
 * original; R8 merged them into AbstractC3138). Falls back to LIST when the server lacks MLST.
 */
object FtpMlsdParser {
    // obf: AbstractC3138

    // obf: f11279 — synthetic root FTPFile used when MLST is unavailable and the path has no parent
    val rootEntry: FTPFile = FTPFile().apply {
        rawListing = "Type=dir;Size=4096;Modify=19700101000000;Perm=cdeflmp; /"
        type = FTPFile.DIRECTORY_TYPE
        size = 4096
        timestamp = Calendar.getInstance().apply { timeInMillis = 0 }
        setPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION, true)
        setPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION, true)
        setPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION, true)
        name = "/"
    }

    // obf: m5564 — stat one entry via MLST (or scan the parent's LIST)
    @Throws(IOException::class)
    fun FTPClient.mlstFile(pathname: String): FTPFile? {
        if (hasFeature("MLST")) {
            return mlistFile(pathname)
        }
        val file = File(pathname)
        val parent = file.parent ?: return rootEntry
        val files = listFiles(parent) ?: return null
        return files.firstOrNull { it != null && it.name == file.name }
    }

    // obf: m5565 — list a directory via MLSD (or LIST)
    @Throws(IOException::class)
    fun FTPClient.mlsdFiles(pathname: String): Array<FTPFile>? {
        if (!hasFeature("MLST")) {
            return listFiles(pathname)
        }
        return mlistDir(pathname)
    }
}

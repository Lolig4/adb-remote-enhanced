package com.jcapps.adbremote.model

import com.google.gson.annotations.SerializedName

/**
 * The resolved FTP connection parameters derived from the device's [FTPSettings] plus its IP,
 * used by [com.jcapps.adbremote.ui.files.FilesActivity] to build an FTP authority.
 */
data class FTPConfig(
    // obf: f4886 / m2600
    @SerializedName("ip_address") val ipAddress: String,
    // obf: f4885 / m2601
    @SerializedName("port") val port: Int,
    // obf: f4890 / m2597
    @SerializedName("default_dir") val defaultDir: String?,
    // obf: f4888 / m2596
    @SerializedName("fallback_dir") val fallbackDir: String,
    // obf: f4892  (read_only; no accessor in release)
    @SerializedName("read_only") val readOnly: Boolean,
    // obf: f4891 / m2599
    @SerializedName("passive_mode") val passiveMode: Boolean,
    // obf: f4887 / m2598
    @SerializedName("username") val username: String?,
    // obf: f4889 / m2602
    @SerializedName("password") val password: String?,
) {
    // obf: C0824
}

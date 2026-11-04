package com.jcapps.adbremote.ui.install

import com.google.gson.annotations.SerializedName

/** Rebuild addition: entry of the server's `GET /apk/files`. */
data class TvApkFile(
    @SerializedName("path") val path: String,
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("modified") val modified: Long,
    @SerializedName("is_bundle") val isBundle: Boolean,
    @SerializedName("label") val label: String?,
    @SerializedName("package_name") val packageName: String?,
    @SerializedName("version_name") val versionName: String?,
    @SerializedName("version_code") val versionCode: Long,
    @SerializedName("installed_version_code") val installedVersionCode: Long,
)

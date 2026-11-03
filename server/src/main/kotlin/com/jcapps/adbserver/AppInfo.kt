package com.jcapps.adbserver

import com.google.gson.annotations.SerializedName

/**
 * Detailed app info returned by `/app?type=info` (JSON field names as in the dex). The app side
 * parses it into `com.jcapps.adbremote.model.AppDetails`.
 *
 * RECON-UNSURE: class name; R8 stripped toString/equals, so the original name is unknown.
 * RECON-UNSURE: data class or not (no generated members survive); plain class like the app side.
 * Property order = constructor order of the dex (vars = non-final fields).
 */
// obf: C0038
class AppInfo(
    // obf: f84
    @field:SerializedName("name") var name: String,
    // obf: f81
    @field:SerializedName("is_enabled") var isEnabled: Boolean,
    // obf: f92
    @field:SerializedName("is_launcher_available") var isLauncherAvailable: Boolean,
    // obf: f88 (final)
    @field:SerializedName("is_tv_supported") val isTvSupported: Boolean,
    // obf: f97
    @field:SerializedName("is_system_app") var isSystemApp: Boolean,
    // obf: f94
    @field:SerializedName("package_name") var packageName: String,
    // obf: f87
    @field:SerializedName("version_code") var versionCode: Long,
    // obf: f91
    @field:SerializedName("version_name") var versionName: String,
    // obf: f79
    @field:SerializedName("compile_sdk") var compileSdk: Int,
    // obf: f82
    @field:SerializedName("min_sdk") var minSdk: Int,
    // obf: f83
    @field:SerializedName("target_sdk") var targetSdk: Int,
    // obf: f93
    @field:SerializedName("file_path") var filePath: String,
    // obf: f80
    @field:SerializedName("size") var size: Long,
    // obf: f86
    @field:SerializedName("native_library_path") var nativeLibraryPath: String,
    // obf: f90
    @field:SerializedName("data_path") var dataPath: String,
    // obf: f96
    @field:SerializedName("first_install_time") var firstInstallTime: Long,
    // obf: f89
    @field:SerializedName("last_update_time") var lastUpdateTime: Long,
    // obf: f95
    @field:SerializedName("uid") var uid: Int,
    // obf: f98
    @field:SerializedName("launcher_activity") var launcherActivity: String,
    // obf: f85 (final)
    @field:SerializedName("permissions") val permissions: List<String>,
)

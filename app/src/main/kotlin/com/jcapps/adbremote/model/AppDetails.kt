package com.jcapps.adbremote.model

import com.google.gson.annotations.SerializedName

// Gson model of the on-device server's app info JSON. Not a data class (no generated toString/equals).
// RECON-UNSURE: original property order unknown (fields are only read via getters); vars = non-final fields.
class AppDetails(
    // obf: f8073 / m4155
    @SerializedName("name") var name: String,
    // obf: f8083 / m4146
    @SerializedName("package_name") var packageName: String,
    // obf: f8080 / m4160
    @SerializedName("version_name") var versionName: String,
    // obf: f8076 / m4154
    @SerializedName("version_code") var versionCode: Long,
    // obf: f8068 / m4148
    @SerializedName("compile_sdk") var compileSdk: Int,
    // obf: f8071 / m4151
    @SerializedName("min_sdk") var minSdk: Int,
    // obf: f8072 / m4144
    @SerializedName("target_sdk") var targetSdk: Int,
    // obf: f8084 / m4150
    @SerializedName("uid") var uid: Int,
    // obf: f8069 / m4157
    @SerializedName("size") var size: Long,
    // obf: f8085 / m4152
    @SerializedName("first_install_time") var firstInstallTime: Long,
    // obf: f8078 / m4161
    @SerializedName("last_update_time") var lastUpdateTime: Long,
    // obf: f8082 / m4156
    @SerializedName("file_path") var filePath: String,
    // obf: f8079 / m4145
    @SerializedName("data_path") var dataPath: String,
    // obf: f8075 / m4143
    @SerializedName("native_library_path") var nativeLibraryPath: String,
    // obf: f8087 / m4158
    @SerializedName("launcher_activity") var launcherActivity: String?, // nullable: AppInfoActivity null-checks it in the original
    // obf: f8070 / m4153
    @SerializedName("is_enabled") var isEnabled: Boolean,
    // obf: f8086 / m4162
    @SerializedName("is_system_app") var isSystemApp: Boolean,
    // obf: f8081 / m4159
    @SerializedName("is_launcher_available") var isLauncherAvailable: Boolean,
    // obf: f8077 / m4149
    @SerializedName("is_tv_supported") val isTvSupported: Boolean,
    // obf: f8074 / m4147
    @SerializedName("permissions") val permissions: List<String>,
) {
    // obf: C1978
}

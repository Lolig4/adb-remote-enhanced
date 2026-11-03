package com.jcapps.adbremote.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class App(
    // obf: f5133 / m2813
    val isAd: Boolean,
    // obf: f5138 / m2814
    val adPosition: Int,
    // obf: f5135 / m2817
    @SerializedName("name") val name: String,
    // obf: f5134 / m2819
    @SerializedName("is_enabled") val isEnabled: Boolean,
    // obf: f5137 / m2816
    @SerializedName("is_launcher_available") val isLauncherAvailable: Boolean,
    // obf: f5141 / m2821
    @SerializedName("is_tv_supported") val isTvSupported: Boolean,
    // obf: f5136 / m2818
    @SerializedName("is_system_app") val isSystemApp: Boolean,
    // obf: f5139 / m2820
    @SerializedName("package_name") val packageName: String,
    // obf: f5140 / m2815
    @SerializedName("version_code") val versionCode: Long,
    // obf: f5142 / m2812
    @SerializedName("version_name") val versionName: String,
) : Parcelable {
    // obf: C0916
}

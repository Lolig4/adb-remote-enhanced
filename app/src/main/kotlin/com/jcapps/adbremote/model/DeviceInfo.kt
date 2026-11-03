package com.jcapps.adbremote.model

import com.google.gson.annotations.SerializedName

// obf: C1702
data class DeviceInfo(
    // obf: f7273
    @SerializedName("android_version") val androidVersion: String,
    // obf: f7270
    @SerializedName("sdk_version") val sdkVersion: Int,
    // obf: f7277
    @SerializedName("incremental") val incremental: String,
    // obf: f7275
    @SerializedName("device") val device: String,
    // obf: f7280
    @SerializedName("manufacturer") val manufacturer: String,
    // obf: f7279
    @SerializedName("model") val model: String,
    // obf: f7274
    @SerializedName("brand") val brand: String,
    // obf: f7276
    @SerializedName("board") val board: String,
    // obf: f7269
    @SerializedName("supported_ABIs") val supportedABIs: List<String>,
    // obf: f7271
    @SerializedName("cpu_info") val cpuInfo: String,
    // obf: f7272
    @SerializedName("memory_info") val memoryInfo: String,
    // obf: f7278
    @SerializedName("battery_info") val batteryInfo: String,
)

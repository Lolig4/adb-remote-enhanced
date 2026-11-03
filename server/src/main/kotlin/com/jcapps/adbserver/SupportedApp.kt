package com.jcapps.adbserver

import com.google.gson.annotations.SerializedName

/** An activity that can open a given uri/mime type, returned by `/supported-apps`. */
// obf: C0134 (toString "SupportedApp(name=..., packageName=..., componentName=...)" -> data class)
data class SupportedApp(
    // obf: f289
    @field:SerializedName("name") val name: String,
    // obf: f288
    @field:SerializedName("package_name") val packageName: String,
    // obf: f290
    @field:SerializedName("component_name") val componentName: String,
)

package com.jcapps.adbremote.model

import com.google.gson.annotations.SerializedName

/**
 * A system app the device advertises as able to open a file remotely (e.g. a video player).
 * Returned by the backend / device query and shown in [DialogOpenRemotelyWith].
 */
data class SupportedApp(
    // obf: f12190 / m5938
    @SerializedName("name") val name: String,
    // obf: f12189 / m5940
    @SerializedName("package_name") val packageName: String,
    // obf: f12191 / m5939
    @SerializedName("component_name") val componentName: String,
) {
    // obf: C3396
}

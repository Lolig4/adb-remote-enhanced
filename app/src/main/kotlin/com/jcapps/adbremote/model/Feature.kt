package com.jcapps.adbremote.model

import com.google.gson.annotations.SerializedName

/** Trackable feature areas (see [com.jcapps.adbremote.utils.FeatureUsage]). */
// obf: EnumC2313
enum class Feature {
    // obf: f9017
    @SerializedName("keyboard")
    KEYBOARD,

    // obf: f9022
    @SerializedName("trackpad")
    TRACKPAD,

    // obf: f9019
    @SerializedName("apps")
    APPS,

    // obf: f9018
    @SerializedName("screenshot")
    SCREENSHOT,

    // obf: f9021
    @SerializedName("clipboard")
    CLIPBOARD,

    // obf: f9024
    @SerializedName("ntp")
    NTP,

    // obf: f9020
    @SerializedName("files")
    FILES,
}

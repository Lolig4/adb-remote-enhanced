package com.jcapps.adbremote.model

/** A HID keyboard usage (keyCode) with an optional HID modifier usage (e.g. 225 = Left Shift). */
data class VirtualKey(
    // obf: f13019
    val keyCode: Int,
    // obf: f13018
    val modifierKey: Int? = null,
) {
    // obf: C3668
}

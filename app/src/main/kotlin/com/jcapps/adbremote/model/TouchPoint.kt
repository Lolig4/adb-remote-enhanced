package com.jcapps.adbremote.model

data class TouchPoint(
    // obf: f5767
    var x: Float,
    // obf: f5766
    var y: Float,
    // RECON-UNSURE: constant-folded by R8 (only visible in toString/hashCode as 255); never passed.
    val alpha: Int = 255,
) {
    // obf: C1131
}

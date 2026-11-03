package com.jcapps.adbremote.model

data class Position(
    // obf: f5888
    var x: Float = 0f,
    // obf: f5887
    var y: Float = 0f,
    // obf: f5892
    var xPow: Float = 0f,
    // obf: f5890
    var yPow: Float = 0f,
    // obf: f5894
    var roundedX: Float = 0f,
    // obf: f5893
    var roundedY: Float = 0f,
    // obf: f5889
    var isFocusDown: Boolean = false,
    // obf: f5891
    var pointerId: Int = 0,
    // obf: f5886
    var currentTimeMillis: Long = 0L,
) {
    // obf: C1172 — RECON-UNSURE: defaults (only the no-arg constructor survived, in HidMouse C1082)
}
